// -*- c-file-style: "bsd" -*-
#include "../pintable.h"
#include "../pincushion.h"
#include <stdlib.h>
#include "lib/timeval.h"
#include "lib/message.h"
#include <signal.h>
#include "lib/test.h"

struct timeval now;

START_TEST(pintable)
{
	PT_Init();
	struct timeval timestamp;
	struct timeval times[10] = {{10, 0}, {20,0}, 
				    {30,0}, {5,0}, 
				    {13, 0}, {15,0}, 
				    {15,5}, {14,5},
				    {300,100}, {300,50}};
	
	gettimeofday(&now, NULL);
	int j, num;
	
	for (j = 0; j < 10; j++) {
		timestamp = timeval_sub(now,times[j]);
		PT_Insert(timestamp,j);
	}
	
	fail_unless(PT_GetEntry(9)->pin == 8, "Didn't sort correctly");
	fail_unless(PT_GetEntry(0)->pin == 3, "Didn't sort correctly");

	timestamp.tv_sec = 400;
	PT_Entry_t *e = PT_Find(timestamp, &num);
	fail_unless(num == 10, "Didn't find all of the pins");
	PT_AddRef(e);

	fail_unless(PT_GetEntry(9)->ref == 1, "Didn't add reference correctly");
	
	PT_Clean();
	
	fail_unless((tail+1)->pin == 8, "Didn't keep referenced pin");
		
	PT_RemoveRef(PT_GetEntry(9));
	
	fail_unless((tail+1)->pin == 2, "Didn't removed unreferenced pins");
	
	timestamp.tv_sec = 18;
	PT_Insert(timeval_sub(now,timestamp),1);
	
	fail_unless(PT_GetEntry(6)->pin == 1, "Didn't handle pin reinsert correctly");

	PT_Find(timestamp, &num);
	fail_unless(num == 6, "Didn't find the right number of entries");
	
}
END_TEST;

START_TEST(overflow)
{
        PT_Init();

        struct timeval timestamp;
        gettimeofday(&timestamp, NULL);

        timestamp.tv_sec--;
        for (int i = 0; i < MAX_PINS + 20; ++i) {
                timestamp.tv_usec = i;
                PT_Insert(timestamp, i);
        }

        // First pin listed should be 1043.  Should list 1023 pins
        PT_Print();
}
END_TEST;

int
main(void)
{
	Suite *s = suite_create("pincushion");

	TCase *tc = tcase_create("Pintable tests");
	tcase_add_test(tc, pintable);
	// This test generally requires inspection
	//tcase_add_test(tc, overflow);
	suite_add_tcase(s, tc);

	return Test_Main(s);
}
