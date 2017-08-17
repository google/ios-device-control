/*
 * idevicewebinspectorproxy.c
 * Proxy a webinspector connection from device for remote debugging
 *
 * Copyright (c) 2013 Yury Melnichek All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA 
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <time.h>

#include <libimobiledevice/libimobiledevice.h>
#include <libimobiledevice/webinspector.h>

#include "endianness.h"
#include "common/socket.h"
#include "common/thread.h"

#define info(...) fprintf(stdout, __VA_ARGS__); fflush(stdout)
#define debug(...) if(debug_mode) { fprintf(stdout, __VA_ARGS__); fflush(stdout); }

static int debug_mode = 0;
static int quit_flag = 0;

typedef struct {
	int server_fd;
	int client_fd;
	uint16_t local_port;
	idevice_t device;
	webinspector_client_t inspector;
	uint32_t timeout;
	int format_xml;
	volatile int stop_ctod;
	volatile int stop_dtoc;
} socket_info_t;

static void clean_exit(int sig)
{
	fprintf(stderr, "Exiting...\n");
	quit_flag++;
}

static void print_usage(int argc, char **argv)
{
	char *name = NULL;

	name = strrchr(argv[0], '/');
	printf("Usage: %s [OPTIONS] <PORT>\n", (name ? name + 1: argv[0]));
	printf("Proxy webinspector connection from device to a local socket at PORT.\n");
	printf("  -d, --debug\t\tenable communication debugging\n");
	printf("  -u, --udid UDID\ttarget specific device by its 40-digit device UDID\n");
	printf("  -h, --help\t\tprints usage information\n");
	printf("  -t, --timeout MSEC\t\tchange timeout when receiving data\n");
	printf("\n");
}

static int send_message(int fd, char *data, size_t message_length)
{
	int current_length = 0;
	do {
		int send_length = socket_send(fd, data + current_length, message_length - current_length);
		if (send_length <= 0) {
			return send_length;
		}
		current_length += send_length;
	} while (current_length < message_length);
	return message_length;
}

static int receive_message_timeout(int fd, char *data, size_t message_length, int flags, unsigned int timeout)
{
	int current_length = 0;
	do {
		int recv_length = socket_receive_timeout(fd, data + current_length, message_length - current_length, flags, timeout);
		if (recv_length == 0 || recv_length == -11) {
			continue;
		} else if (recv_length < 0) {
			return recv_length;
		}
		current_length += recv_length;
	} while (current_length < message_length);
	return message_length;
}

static void *thread_device_to_client(void *data)
{
	socket_info_t* socket_info = (socket_info_t*)data;
	webinspector_error_t res = WEBINSPECTOR_E_UNKNOWN_ERROR;

	uint32_t network_mlen = 0;
	uint32_t message_length = 0;
	int sent;
	char * buf = NULL;
	plist_t message = NULL;

	debug("%s: started thread...\n", __func__);

	debug("%s: client_fd = %d\n", __func__, socket_info->client_fd);
	debug("%s: server fd = %d\n", __func__, socket_info->server_fd);

	while (!quit_flag && !socket_info->stop_dtoc && socket_info->client_fd > 0 && socket_info->server_fd > 0) {
		debug("%s: receiving data from device...\n", __func__);

		res = webinspector_receive_with_timeout(socket_info->inspector, &message, socket_info->timeout);
		if (res != WEBINSPECTOR_E_SUCCESS) {
			fprintf(stderr, "webinspector_receive_with_timeout failed: %d\n", res);
			continue;
		}
		if (socket_info->format_xml) {
			plist_to_xml(message, &buf, &message_length);
		} else {
			plist_to_bin(message, &buf, &message_length);
		}
		if (!buf || message_length == 0) {
			fprintf(stderr, "Error converting plist to binary.\n");
			break;
		}

		if (message) {
			plist_free(message);
			message = NULL;
		}

		/* send message length to client */
		debug("%s: sending length to client...\n", __func__);
		network_mlen = htobe32(message_length);
		sent = send_message(socket_info->client_fd, (char*)&network_mlen, sizeof(network_mlen));
		if (sent <= 0) {
			fprintf(stderr, "Send message length failed: %s\n", strerror(errno));
			break;
		} else {
			debug("%s: pushed %d bytes to client\n", __func__, sent);

			/* send message data to client */
			debug("%s: sending data to client...\n", __func__);
			sent = send_message(socket_info->client_fd, buf, message_length);
			if (sent <= 0) {
				fprintf(stderr, "Send message failed: %s\n", strerror(errno));
				break;
			} else {
				// sending succeeded, receive from device
				debug("%s: pushed %d bytes to client\n", __func__, sent);
			}
		}

		if (buf) {
			free(buf);
			buf = NULL;
		}
	}

	debug("%s: shutting down...\n", __func__);

	socket_shutdown(socket_info->client_fd, SHUT_RDWR);
	socket_close(socket_info->client_fd);

	socket_info->client_fd = -1;
	socket_info->stop_ctod = 1;

	return NULL;
}

static void *thread_client_to_device(void *data)
{
	socket_info_t* socket_info = (socket_info_t*)data;
	webinspector_error_t res = WEBINSPECTOR_E_UNKNOWN_ERROR;

	int recv_len;
	char buffer[131072];
	plist_t message = NULL;
	uint32_t network_mlen = 0;
	uint32_t message_length = 0;
	thread_t dtoc = 0;

	debug("%s: started thread...\n", __func__);

	debug("%s: client_fd = %d\n", __func__, socket_info->client_fd);
	debug("%s: server_fd = %d\n", __func__, socket_info->server_fd);

	while (!quit_flag && !socket_info->stop_ctod && socket_info->client_fd > 0 && socket_info->server_fd > 0) {
		debug("%s: receiving data from client...\n", __func__);

		/* attempt to read incoming message length from client */
		recv_len = receive_message_timeout(socket_info->client_fd, (char *)&network_mlen, sizeof(network_mlen), 0, socket_info->timeout);
		if (recv_len < 0) {
			fprintf(stderr, "Receive message length failed: %s %d %d\n", strerror(errno), errno, recv_len);
			break;
		}
		message_length = be32toh(network_mlen);
		if (message_length == 0 || message_length >= sizeof(buffer)) {
			fprintf(stderr, "Invalid message length: %d\n", message_length);
			break;
		}

		/* attempt to read incoming data from client */
		recv_len = receive_message_timeout(socket_info->client_fd, buffer, message_length, 0, socket_info->timeout);
		if (recv_len < 0) {
			fprintf(stderr, "Receive message failed: %s %d %d\n", strerror(errno), errno, recv_len);
			break;
		}

		/* convert buffer to a message */
		if ((message_length > 8) && !memcmp(buffer, "bplist00", 8)) {
			plist_from_bin(buffer, message_length, &message);
		} else if ((message_length > 5) && !memcmp(buffer, "<?xml", 5)) {
			plist_from_xml(buffer, message_length, &message);
		} else {
			fprintf(stderr, "Invalid input %u: %*s\n", message_length, message_length, buffer);
			break;
		}

		if (!socket_info->inspector) {
			debug("%s: connecting to inspector...\n", __func__);
			webinspector_error_t error = webinspector_client_start_service(socket_info->device, &socket_info->inspector, "idevicewebinspectorproxy");
			if (error != WEBINSPECTOR_E_SUCCESS) {
				fprintf(stderr, "Could not connect to the webinspector! Error: %i\n", error);
				break;
			}
		}
		if (!dtoc) {
			debug("%s: Starting device-to-client thread...\n", __func__);
			if (thread_new(&dtoc, thread_device_to_client, data) != 0) {
				fprintf(stderr, "Failed to start device to client thread...\n");
				break;
			}
		}

		/* forward data to device */
		debug("%s: sending data to device...\n", __func__);

		res = webinspector_send(socket_info->inspector, message);
		if (res != WEBINSPECTOR_E_SUCCESS) {
			fprintf(stderr, "send failed: %s\n", strerror(errno));
			break;
		}

		// sending succeeded, receive from device
		debug("%s: sent %d bytes to device\n", __func__, message_length);
	}

	debug("%s: shutting down...\n", __func__);

	socket_shutdown(socket_info->client_fd, SHUT_RDWR);
	socket_close(socket_info->client_fd);

	socket_info->client_fd = -1;
	socket_info->stop_dtoc = 1;

	if (dtoc) {
		/* join other thread to allow it to stop */
		thread_join(dtoc);
		thread_free(dtoc);
	}

	return NULL;
}

static void* connection_handler(void* data)
{
	socket_info_t* socket_info = (socket_info_t*)data;
	thread_t ctod;

	debug("%s: client_fd = %d\n", __func__, socket_info->client_fd);

	/* spawn client to device thread */
	if (thread_new(&ctod, thread_client_to_device, data) != 0) {
		fprintf(stderr, "Failed to start client to device thread...\n");
	}

	/* join the fun */
	thread_join(ctod);
	thread_free(ctod);

	/* shutdown client socket */
	socket_shutdown(socket_info->client_fd, SHUT_RDWR);
	socket_close(socket_info->client_fd);

	/* shutdown server socket if we have to terminate to unblock the server loop */
	if (quit_flag) {
		socket_shutdown(socket_info->server_fd, SHUT_RDWR);
		socket_close(socket_info->server_fd);
	}

	return NULL;
}

int main(int argc, char **argv)
{
	idevice_error_t ret = IDEVICE_E_UNKNOWN_ERROR;
	thread_t th;
	const char* udid = NULL;
	int result = EXIT_SUCCESS;
	int i;
	socket_info_t socket_info;

	socket_info.device = NULL;
	socket_info.inspector = NULL;
	socket_info.local_port = 0;
	socket_info.timeout = 1000;
	socket_info.format_xml = 0;
	socket_info.stop_ctod = 0;
	socket_info.stop_dtoc = 0;

	/* bind signals */
#ifndef WIN32
	struct sigaction sa;
	struct sigaction si;
	memset(&sa, '\0', sizeof(struct sigaction));
	memset(&si, '\0', sizeof(struct sigaction));

	sa.sa_handler = clean_exit;
	sigemptyset(&sa.sa_mask);

	si.sa_handler = SIG_IGN;
	sigemptyset(&si.sa_mask);

	sigaction(SIGINT, &sa, NULL);
	sigaction(SIGTERM, &sa, NULL);
	sigaction(SIGQUIT, &sa, NULL);
	sigaction(SIGPIPE, &si, NULL);
#else
	/* bind signals */
	signal(SIGINT, clean_exit);
	signal(SIGTERM, clean_exit);
#endif

	/* parse cmdline arguments */
	for (i = 1; i < argc; i++) {
		if (!strcmp(argv[i], "-d") || !strcmp(argv[i], "--debug")) {
			debug_mode = 1;
			idevice_set_debug_level(1);
			socket_set_verbose(3);
			continue;
		}
		else if (!strcmp(argv[i], "-u") || !strcmp(argv[i], "--udid")) {
			i++;
			if (!argv[i] || (strlen(argv[i]) != 40)) {
				print_usage(argc, argv);
				return 0;
			}
			udid = argv[i];
			continue;
		}
		else if (!strcmp(argv[i], "-t") || !strcmp(argv[i], "--timeout")) {
			i++;
			if (!argv[i] || (atoi(argv[i]) <= 0)) {
				print_usage(argc, argv);
				return 0;
			}
			socket_info.timeout = atoi(argv[i]);
			continue;
		}
		else if (!strcmp(argv[i], "-x") || !strcmp(argv[i], "--xml")) {
			socket_info.format_xml = 1;
		}
		else if (!strcmp(argv[i], "-h") || !strcmp(argv[i], "--help")) {
			print_usage(argc, argv);
			return EXIT_SUCCESS;
		}
		else if (atoi(argv[i]) > 0) {
			socket_info.local_port = atoi(argv[i]);
			continue;
		}
		else {
			print_usage(argc, argv);
			return EXIT_SUCCESS;
		}
	}

	/* a PORT is mandatory */
	if (!socket_info.local_port) {
		fprintf(stderr, "Please specify a PORT.\n");
		print_usage(argc, argv);
		goto leave_cleanup;
	}

	/* start services and connect to device */
	ret = idevice_new(&socket_info.device, udid);
	if (ret != IDEVICE_E_SUCCESS) {
		if (udid) {
			fprintf(stderr, "No device found with udid %s, is it plugged in?\n", udid);
		} else {
			fprintf(stderr, "No device found, is it plugged in?\n");
		}
		result = EXIT_FAILURE;
		goto leave_cleanup;
	}

	/* create local socket */
	socket_info.server_fd = socket_create(socket_info.local_port);
	if (socket_info.server_fd < 0) {
		fprintf(stderr, "Could not create socket\n");
		result = EXIT_FAILURE;
		goto leave_cleanup;
	}

	while (!quit_flag) {
		debug("%s: Waiting for connection on local port %d\n", __func__, socket_info.local_port);

		/* wait for client */
		socket_info.client_fd = socket_accept(socket_info.server_fd, socket_info.local_port);
		if (socket_info.client_fd < 0) {
			debug("%s: Continuing...\n", __func__);
			continue;
		}

		debug("%s: Handling new client connection...\n", __func__);

		if (thread_new(&th, connection_handler, (void*)&socket_info) != 0) {
			fprintf(stderr, "Could not start connection handler.\n");
			socket_shutdown(socket_info.server_fd, SHUT_RDWR);
			socket_close(socket_info.server_fd);
		}
	}

	debug("%s: Shutting down webinspector proxy...\n", __func__);

leave_cleanup:
	if (socket_info.inspector) {
		webinspector_client_free(socket_info.inspector);
	}
	if (socket_info.device) {
		idevice_free(socket_info.device);
	}

	return result;
}
