/**
 * run app on iDevice using com.apple.debugserver - by <predrg@gmail.com>
 *
 * based on:
 *   ideviceinstaller - Copyright (C) 2010 Nikias Bassen <nikias@gmx.li>
 *   remote.c - from gdb
 *
 * Licensed under the GNU General Public License Version 2
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more profile.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 
 * USA
 */

/*
  build me with:
  $ gcc -g -pthread idevice-app-runner.c -o idevice-app-runner /usr/lib/libimobiledevice.so
*/

#include <getopt.h>
#include <math.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <libimobiledevice/installation_proxy.h>
#include <libimobiledevice/libimobiledevice.h>
#include <libimobiledevice/lockdown.h>
#include <plist/plist.h>

#ifndef BOOL
#define BOOL int
#endif

static BOOL user_quit = 0;
static BOOL app_quit = 0;

/**
 * signal handler function for cleaning up properly
 */
static void on_signal(int sig) {
    fprintf(stderr, "Exiting...\n");
    user_quit = 1;
}

void print_usage(int argc, char **argv) {
    char *name = strrchr(argv[0], '/');
    name = (name ? name + 1 : argv[0]);
    fprintf(stderr,
        "Usage: %s OPTIONS\n"
        "Run apps on an iDevice.\n"
        "\n"
        "  -u, --udid UDID\ttarget specific device by its 40-digit device UDID.\n"
        "  -s, --start APPID\tstart app specified by APPID (required).\n"
        "  -D<name>=<value>\tset an environment variable.\n"
        "  --args ARG...\t\tset command-line arguments.\n"
        "  -h, --help\t\tprints usage information\n"
        "  -d, --debug\t\tenable communication debugging\n"
        "\n", name);
}

void parse_options(int argc, char **argv,
        char **to_uuid, char **to_app_id, char ***to_env, char ***to_args,
        BOOL *to_debug_flag);

int connect_to_debugserver(char *uuid, char *app_id,
        char **to_app_path, idevice_connection_t *to_connection);

plist_t get_apps(idevice_t phone, lockdownd_client_t client);
char **get_app_ids(plist_t apps);
char *get_app_path(const char *app_id, plist_t apps);

char *tohex(char *to_s, const char *from_s, size_t n);
char *fromhex(char *to_s, const char *from_s, size_t n);


struct in_struct;
typedef struct in_struct *in_t;
in_t in_new(idevice_connection_t connection, BOOL debug_flag,
        BOOL *error_flag, size_t buf_len);
void in_free(in_t in);

int read_pkt(in_t in, char **to_s, size_t *to_n, BOOL allow_empty);
int read_pkt_assert(in_t in, const char *expected);


struct out_struct;
typedef struct out_struct *out_t;
out_t out_new(idevice_connection_t connection, BOOL debug_flag,
        BOOL *error_flag);
void out_free(out_t out);

void write_pkt(out_t out, const char *s);


char *create_env_packet(const char *env) {
    char *ret = calloc(2*strlen(env)+28, sizeof(char));
    char *t = ret;
    t = stpcpy(t, "$QEnvironmentHexEncoded:");
    t = tohex(t, env, strlen(env));
    t = stpcpy(t, "#00");
    return ret;
}

// "$A," + len(app_path) + ",0," + hex(app_path)
//    + [ "," len(args[i]) ","+i+"," + hex(args[i]) ]*
//    + "#00"
char *create_args_packet(const char *app_path, char **args) {
    size_t len = 5; // $A #00
    size_t i;
    for (i = 0; ; i++) {
        const char *s = (i ? (args ? args[i-1] : NULL) : app_path);
        if (!s) break;
        size_t n = strlen(s);
        #define numlen(v) ((v) ? (int)(log10((v)+1)+1) : 1)
        len += (i?3:2) + numlen(2*n) + numlen(i) + 2*n;
    }
    char *ret = calloc(len + 1, sizeof(char));
    char *t = stpcpy(ret, "$A");
    for (i = 0; ; i++) {
        const char *s = (i ? (args ? args[i-1] : NULL) : app_path);
        if (!s) break;
        size_t n = strlen(s);
        t += sprintf(t, "%s%d,%d,", (i ? "," : ""), 2*(int)n, (int)i);
        t = tohex(t, s, n);
    }
    t = stpcpy(t, "#00");
    return ret;
}

int main(int argc, char **argv) {
    // Map ctrl-c to user_quit=1
    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
#ifndef WIN32
    signal(SIGQUIT, on_signal);
    signal(SIGPIPE, SIG_IGN);
#endif
    char *uuid = NULL;
    char *app_id = NULL;
    char **env = NULL;
    char **args = NULL;
    BOOL debug_flag = 0;
    size_t buf_len = 16*1024;
    parse_options(argc, argv, &uuid, &app_id, &env, &args, &debug_flag);

    char *app_path = NULL;
    idevice_connection_t connection = NULL;
    if (connect_to_debugserver(uuid, app_id, &app_path, &connection)) {
        return -1;
    }

    BOOL error_flag = 0;
    in_t in = in_new(connection, debug_flag, &error_flag, buf_len);
    out_t out = out_new(connection, debug_flag, &error_flag);

    // Begin lldb remote serial protocol
    //
    // Some useful links:
    // http://opensource.apple.com/source/lldb/lldb-159/docs/lldb-gdb-remote.txt
    // http://davis.lbl.gov/Manuals/GDB/gdb_31.html
    // http://sourceware.org/gdb/onlinedocs/gdb/Packets.html
    // http://www.embecosm.com/appnotes/ean4/\
    //     embecosm-howto-rsp-server-ean4-issue-2.html

    // Disable acks
    write_pkt(out, "$QStartNoAckMode#b0");
    read_pkt_assert(in, "+");
    read_pkt_assert(in, "$OK#9a");
    write_pkt(out, "+");

    // Set environment variables
    if (env) {
        char **s;
        for (s = env; *s; s++) {
            char *encoded_env = create_env_packet(*s);
            write_pkt(out, encoded_env);
            free(encoded_env);
            read_pkt_assert(in, "$OK#00");
        }
    }

    // Set app_path and args
    char *encoded_app_path = create_args_packet(app_path, args);
    write_pkt(out, encoded_app_path);
    free(encoded_app_path);

    read_pkt_assert(in, "$OK#00");

    // Check status
    write_pkt(out, "$qLaunchSuccess#00");
    read_pkt_assert(in, "$OK#00");

    // Select all threads
    write_pkt(out, "$Hc-1#00");
    read_pkt_assert(in, "$OK#00");

    // Continue
    write_pkt(out, "$c#00");

    // Read stdout from phone
    int ret = 1;
    int spin_counter = 0;
    while (!user_quit) {
        char *s = NULL;
        size_t n = 0;
        if (read_pkt(in, &s, &n, 1)) {
            break;
        }
        if (n == 0) {
            if (++spin_counter > 5) {
                // Our read_pkt should wait 1s for input, but just to make
                // sure that we don't spin, let's add a sleep here:
                sleep(1);
                spin_counter = 0;
            }

            // GDB won't tell us if the app has died or the user did an
            // exit.
            //
            // If it's been a long time, we could send a break:
            //   write_pkt(out, "\3");
            // then look for:
            //   !strncmp(s, "$T" ...
            // and continue via:
            //   write_pkt(out, "$c#00");
            // If we never get a "$T" then maybe it's dead.
            continue;
        }
        spin_counter = 0;
        if (n == 4 && !strncmp(s, "$#00", 4)) {
            continue;
        }
        if (n > 5 && !strncmp(s, "$O", 2) && !strncmp(s+n-3, "#00", 3)) {
            // Print to stdout
            fromhex(s, s+2, n-5);
            printf("%s", s);
            fflush(stdout);
            write_pkt(out, "$OK#00");
            continue;
        }
        if (n > 2 && !strncmp(s, "$T", 2)) {
            // Crashed?
            break;
        }
        if (n > 5 && (!strncmp(s, "$W", 2) || !strncmp(s, "$X", 2)) &&
                !strncmp(s+n-3, "#00", 3)) {
            // Exit
            app_quit = 1;
            fromhex(s, s+2, n-3);
            ret = atoi(s);
            write_pkt(out, "$OK#00");
            break;
        }
        fprintf(stderr, "recv (%.*s) instead of expected ($O<stdout>#00)\n",
                (int)n, s);
        break;
    }

    // Send kill
    write_pkt(out, "$k#00");

    idevice_disconnect(connection);

    // Optional cleanup:
    if (env) {
        char **s;
        for (s = env; *s; s++) {
            free(*s);
        }
        free(env);
    }
    if (args) {
        char **a;
        for (a = args; *a; a++) {
            free(*a);
        }
        free(args);
    }
    in_free(in);
    out_free(out);
    free(app_id);
    free(app_path);
    free(uuid);

    return ret;
}

void parse_options(int argc, char **argv,
        char **to_uuid, char **to_app_id, char ***to_env, char ***to_args,
        BOOL *to_debug_flag) {
    static struct option longopts[] = {
        {"udid", 1, NULL, 'u'},
        {"start", 1, NULL, 's'},
        {"D", 1, NULL, 'D'},
        {"args", 0, NULL, 'a'},
        {"help", 0, NULL, 'h'},
        {"debug", 0, NULL, 'd'},

        // Old arg name, conflicts with `ideviceinstaller -r` restore
        {"run", 1, NULL, 'r'},

        // ideviceinstaller style
        {"uuid", 1, NULL, 'U'},

        {NULL, 0, NULL, 0}
    };

    int env_len = 0;

    while (1) {
        int c = getopt_long(argc, argv, "-u:s:D:hdr:U:a", longopts, (int *) 0);
        if (c == -1 || c == 1) {
            break;
        }

        switch (c) {
        case 'h':
            print_usage(argc, argv);
            exit(0);
        case 'u':
        case 'U':
            if (strlen(optarg) != 40) {
                fprintf(stderr, "%s: invalid UDID specified (length != 40)\n",
                        argv[0]);
                print_usage(argc, argv);
                exit(2);
            }
            *to_uuid = strdup(optarg);
            break;
        case 's':
        case 'r':
            *to_app_id = strdup(optarg);
            break;
        case 'D':
            *to_env = realloc(*to_env, (env_len+2)*sizeof(char*));
            (*to_env)[env_len] = strdup(optarg);
            (*to_env)[++env_len] = NULL;
            break;
        case 'd':
            *to_debug_flag = 1;
            break;
        case 'a':
            {
                size_t n = argc - optind;
                *to_args = calloc(n+1, sizeof(char*));
                size_t i;
                for (i = 0; i < n; i++) {
                    (*to_args)[i] = strdup(argv[optind++]);
                }
            }
            break;
        default:
            print_usage(argc, argv);
            exit(2);
        }
    }

    if ((argc - optind) > 0 || !*to_app_id) {
        print_usage(argc, argv);
        exit(2);
    }
}

int connect_to_debugserver(char *uuid, char *app_id,
        char **to_app_path, idevice_connection_t *to_connection) {
    idevice_t phone = NULL;
    lockdownd_client_t client = NULL;
    plist_t apps = NULL;
    lockdownd_service_descriptor_t service = NULL;
    int ret = -1;

    // Get phone
    if (IDEVICE_E_SUCCESS != idevice_new(&phone, uuid)) {
        fprintf(stderr, "No iPhone found, is it plugged in?\n");
        goto leave_cleanup;
    }

    // Connect to lockdownd
    if (LOCKDOWN_E_SUCCESS != lockdownd_client_new_with_handshake(
            phone, &client, "idevice-app-runner")) {
        fprintf(stderr, "Could not connect to lockdownd. Exiting.\n");
        goto leave_cleanup;
    }

    // Start debugserver
    if ((lockdownd_start_service(client, "com.apple.debugserver",
          &service) != LOCKDOWN_E_SUCCESS) || !service->port) {
        // This happens if you reboot the phone and don't have Xcode running.
        // The workaround is to keep Xcode running in the background.
        // TBD fix this!
        fprintf(stderr, "Could not start com.apple.debugserver!\n");
        goto leave_cleanup;
    }

    // Connect to debugserver
    if (idevice_connect(phone, service->port, to_connection) != IDEVICE_E_SUCCESS) {
        fprintf(stderr, "idevice_connect failed!\n");
        goto leave_cleanup;
    }

    // Get app path
    if (app_id && !*to_app_path) {
        apps = get_apps(phone, client);
        *to_app_path = get_app_path(app_id, apps);
        if (!*to_app_path) {
            if (app_id && !strncmp(app_id, "/", 1)) {
                // Backwards-compatible path from `ideviceinstaller -l -o xml`
                *to_app_path = strdup(app_id);
            } else {
                fprintf(stderr, "Unknown APPID (%s) is not in:\n", app_id);
                char **app_ids = get_app_ids(apps);
                char **tail;
                for (tail = app_ids; *tail; tail++) {
                    fprintf(stderr, "\t%s\n", *tail);
                    free(*tail);
                }
                free(app_ids);
                goto leave_cleanup;
            }
        }
    }

    ret = 0;

leave_cleanup:
    plist_free(apps);
    if (ret) {
        idevice_disconnect(*to_connection);
        *to_connection = NULL;
    }
    lockdownd_service_descriptor_free(service);
    lockdownd_client_free(client);
    idevice_free(phone);

    return ret;
}

plist_t get_apps(idevice_t phone, lockdownd_client_t client) {
    lockdownd_service_descriptor_t service = NULL;
    const char * service_name = "com.apple.mobile.installation_proxy";
    if ((lockdownd_start_service(client, service_name, &service)
            != LOCKDOWN_E_SUCCESS) || !service->port) {
        fprintf(stderr, "Could not start %s!\n", service_name);
        return NULL;
    }

    instproxy_client_t ipc = NULL;
    if (instproxy_client_new(phone, service, &ipc) != INSTPROXY_E_SUCCESS) {
        fprintf(stderr, "Could not connect to installation_proxy!\n");
        return NULL;
    }

    plist_t client_opts = instproxy_client_options_new();
    instproxy_client_options_add(client_opts, "ApplicationType", "User", NULL);
    instproxy_error_t err;
    plist_t apps = NULL;
    err = instproxy_browse(ipc, client_opts, &apps);
    instproxy_client_options_free(client_opts);
    instproxy_client_free(ipc);
    lockdownd_service_descriptor_free(service);
    if (err != INSTPROXY_E_SUCCESS) {
        plist_free(apps);
        return NULL;
    }

    return apps;
}

char **get_app_ids(plist_t apps) {
    size_t len = 0;
    uint32_t i;
    uint32_t n = plist_array_get_size(apps);
    for (i = 0; i < n; i++) {
        plist_t dict = plist_array_get_item(apps, i);
        plist_t item = plist_dict_get_item(dict, "CFBundleIdentifier");
        if (item) {
            len++;
        }
    }
    char **ret = (char **)calloc(len + 1, sizeof(char **));
    char **tail = ret;
    for (i = 0; i < n; i++) {
        plist_t dict = plist_array_get_item(apps, i);
        plist_t item = plist_dict_get_item(dict, "CFBundleIdentifier");
        if (item) {
            plist_get_string_val(item, tail++);
        }
    }
    return ret;
}

char *get_app_path(const char *app_id, plist_t apps) {
    if (!app_id || !apps) {
        return NULL;
    }
    uint32_t i;
    uint32_t n = plist_array_get_size(apps);
    for (i = 0; i < n; i++) {
        plist_t dict = plist_array_get_item(apps, i);
        plist_t item = plist_dict_get_item(dict, "CFBundleIdentifier");
        if (!item) {
            continue;
        }
        char *name;
        plist_get_string_val(item, &name);
        int is_match = (name && !strcmp(name, app_id));
        free(name);
        if (is_match) {
            plist_t path = plist_dict_get_item(dict, "Path");
            if (plist_get_node_type(path) == PLIST_STRING) {
                char *ret = NULL;
                plist_get_string_val(path, &ret);
                return ret;
            }
        }
    }
    return NULL;
}


char int2hex(int x) {
    static const char *hexchars = "0123456789ABCDEF";
    return hexchars[x];
}

int hex2int(char c) {
    if (c >= '0' && c <= '9')
        return c - '0';
    else if (c >= 'a' && c <= 'f')
        return 10 + c - 'a';
    else if (c >= 'A' && c <= 'F')
        return 10 + c - 'A';
    else
        return -1;
}

char *tohex(char *to_s, const char *from_s, size_t n) {
    const char *f = from_s;
    char *t = to_s;
    const char *fend = f + n;
    while (f < fend) {
        *t++ = int2hex(*f >> 4);
        *t++ = int2hex(*f & 0xf);
        f++;
    }
    *t = '\0';
    return t;
}

char *fromhex(char *to_s, const char *from_s, size_t n) {
    const char *f = from_s;
    char *t = to_s;
    const char *fend = f + n;
    while (f < fend) {
        int i1 = hex2int(*f++);
        int i2 = hex2int(*f++);
        *t++ = i1 << 4 | i2;
    }
    *t = '\0';
    return t;
}


struct in_struct {
    idevice_connection_t connection;
    BOOL debug_flag;
    BOOL *error_flag;

    char *buf_begin;
    char *buf_head;
    char *buf_next;
    char *buf_tail;
    char *buf_end;
};

in_t in_new(idevice_connection_t connection, BOOL debug_flag,
        BOOL *error_flag, size_t buf_len) {
    in_t in = (in_t)malloc(sizeof(struct in_struct));
    char *buf = (char *)malloc(buf_len);
    if (!in || !buf) {
        free(in);
        return NULL;
    }
    memset(in, 0, sizeof(struct in_struct));
    in->connection = connection;
    in->debug_flag = debug_flag;
    in->error_flag = error_flag;
    in->buf_begin = buf;
    in->buf_head = buf;
    in->buf_next = buf;
    in->buf_tail = buf;
    in->buf_end = buf + buf_len;
    return in;
}

void in_free(in_t in) {
    if (in) {
        free(in->buf_begin);
        memset(in, 0, sizeof(struct in_struct));
        free(in);
    }
}


struct out_struct {
    idevice_connection_t connection;
    BOOL debug_flag;
    BOOL *error_flag;
};

out_t out_new(idevice_connection_t connection, BOOL debug_flag,
        BOOL *error_flag) {
    out_t out = (out_t)malloc(sizeof(struct out_struct));
    if (!out) {
        return NULL;
    }
    memset(out, 0, sizeof(struct out_struct));
    out->connection = connection;
    out->debug_flag = debug_flag;
    out->error_flag = error_flag;
    return out;
}

void out_free(out_t out) {
    if (out) {
        memset(out, 0, sizeof(struct out_struct));
        free(out);
    }
}


void write_pkt(out_t out, const char *s) {
    if (*out->error_flag) {
        return;
    }
    int n = strlen(s);
    int bytes = 0;
    int err_code = idevice_connection_send(out->connection, s, n,
             (uint32_t*)&bytes);
    if (out->debug_flag) {
        fprintf(stderr, "sent[%d] (%s)\n", bytes, s);
    }
    if (err_code != IDEVICE_E_SUCCESS || bytes != n) {
        if (app_quit && out->debug_flag) {
          fprintf(stderr, "App quit before it could be killed. That's OK.\n");
        } else if (!app_quit) {
          fprintf(stderr, "Send failed, err_code=%d bytes=%d/%d Exiting.\n",
                  err_code, bytes, n);
        }
        *out->error_flag = 1;
    }
    return;
}

int read_char(in_t in, char *to_ch, BOOL *to_allow_empty) {
    if (*in->error_flag) {
        return -1;
    }
    if (in->buf_next == in->buf_tail) {
        // Must read
        size_t avail = in->buf_end - in->buf_tail;
        size_t len = in->buf_end - in->buf_begin;
        if (avail < (len >> 2)) {
            // Make room
            size_t offset = in->buf_head - in->buf_begin;
            if (!avail && !offset) {
                fprintf(stderr, "Recv buffer[%zd] full! %.*s%s\n", len,
                        (len > 20 ? 20 : (int)len), in->buf_begin,
                        (len > 20 ? "..." : ""));
                *in->error_flag = 1;
                return -1;
            }
            size_t used = in->buf_tail - in->buf_head;
            if (offset && used) {
                memmove(in->buf_begin, in->buf_head, used);
            }
            in->buf_head = in->buf_begin;
            in->buf_next = in->buf_begin + used;
            in->buf_tail = in->buf_next;
            avail = in->buf_end - in->buf_tail;
        }

        // If the call requires bytes to be read (to_allow_empty == NULL),
        // we loop up to timeout deadline until we receive some bytes.
        uint32_t bytes = 0;
        time_t start;
        time(&start);
        while (1) {
            int err_code = idevice_connection_receive_timeout(
                  in->connection, in->buf_tail, avail, &bytes, 500);
            if (err_code != IDEVICE_E_SUCCESS) {
                fprintf(stderr, "Recv failed, err_code=%d bytes=%d. Exiting.\n",
                        err_code, bytes);
                *in->error_flag = 1;
                return -1;
            }
            if (bytes == 0 && to_allow_empty) {
                *to_allow_empty = 1;
                return 0;
            }
            if (in->debug_flag) {
                fprintf(stderr, "recv[%d] (%.*s)\n", bytes, bytes, in->buf_tail);
            }
            if (bytes > 0) {
                in->buf_tail += bytes;
                break;
            }
            time_t now;
            time(&now);
            if (difftime(now, start) > 10) {
                fprintf(stderr, "Recv timeout. Exiting.\n");
                *in->error_flag = 1;
                return -1;
            }
            sleep(1);
        }
    }
    if (to_allow_empty) {
        *to_allow_empty = 0;
    }
    *to_ch = *in->buf_next++;
    return 0;
}

int read_pkt(in_t in, char **to_s, size_t *to_n, BOOL allow_empty) {
    if (*in->error_flag) {
        return -1;
    }
    char ch;
    BOOL is_empty = 0;
    if (read_char(in, &ch, (allow_empty ? &is_empty : NULL))) {
        return -1;
    }
    BOOL is_success = 0;
    if (is_empty) {
        is_success = 1;
    } else if (ch == '+') {
        is_success = 1;
    } else if (ch == '$') {
        while (1) {
            if (read_char(in, &ch, NULL)) {
                return -1;
            }
            if (ch == '#') {
                break;
            }
        }
        if (read_char(in, &ch, NULL)) {
            return -1;
        }
        if (hex2int(ch) >= 0) {
            if (read_char(in, &ch, NULL)) {
                return -1;
            }
            if (hex2int(ch) >= 0) {
                is_success = 1;
            }
        }
    }
    size_t n = in->buf_next - in->buf_head;
    if (!is_success) {
        fprintf(stderr, "Received invalid gdb command (%.*s). Exiting.\n",
                (int)n, in->buf_head);
        *in->error_flag = 1;
    }
    *to_s = in->buf_head;
    *to_n = n;
    in->buf_head = in->buf_next;
    return (is_success ? 0 : -1);
}

int read_pkt_assert(in_t in, const char *expected) {
    char  *s = NULL;
    size_t n = 0;
    if (!read_pkt(in, &s, &n, 0)) {
        if (expected && !strncmp(s, expected, n)) {
            return 0;
        }
        fprintf(stderr, "Error: recv (%.*s) instead of expected (%s)\n",
            (int)n, s, expected);
        *in->error_flag = 1;
    }
    return -1;
}
