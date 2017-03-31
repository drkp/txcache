#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <unistd.h>

#define SIZE 6408

// ( 0,24) -> 32
// (25,40) -> 48
// (41,56) -> 64
// (57,72) -> 80
// (73,88) -> 96

// ((v + 7) | 15) + 1

unsigned long
getVSize(void)
{
        int pid = getpid();
        char fname[64];
        FILE *fp;
        unsigned long vsize = 0;

        sprintf(fname, "/proc/%d/stat", pid);
        fp = fopen(fname, "r");
        assert(fp);
        fscanf(fp,
               /*PID*/         "%*d %*s %*c %*d %*d"
               /*session*/     "%*d %*d %*d %*u %*lu"
               /*cminflt*/     "%*lu %*lu %*lu %*lu %*lu"
               /*cutime*/      "%*ld %*ld %*ld %*ld %*ld"
               /*itrealvalue*/ "%*ld %*llu %lu",
               &vsize);
        assert(vsize);
        fclose(fp);
        return vsize;
}

void
alloc(int count, const char *desc)
{
        unsigned long pre = getVSize();
        for (int i = 0; i < count; ++i) {
                void *obj = malloc(SIZE);
                assert(obj);
                memset(obj, 1, SIZE);
        }
        unsigned long post = getVSize();
        double per = (double)(post - pre) / count;
        printf("%s: Went from %ld to %ld (%g per object, %g overhead)\n",
               desc, pre, post, per, per - SIZE);
}

int
main(int argc, char **argv)
{
        alloc(10*1024*1024 / SIZE, "warm");
        alloc(10*1024*1024 / SIZE, "main");
}
