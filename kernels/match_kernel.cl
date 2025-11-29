__kernel void count_matches(__global const uchar *text, const int textLen,
                            __global const uchar *pattern, const int patLen,
                            __global int *outCount) {
    int gid = get_global_id(0);
    if (gid + patLen > textLen) return;
    for (int i = 0; i < patLen; i++) {
        if (text[gid + i] != pattern[i]) return;
    }
    atomic_inc(outCount);
}
