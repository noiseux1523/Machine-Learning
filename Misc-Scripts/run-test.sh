#!/bin/bash

# touch ./runs/2-Fold-50-test-50-train.txt
# sleep 3

# cp -r ./runs/2-Fold-50-test-50-train/ ./runs

# 	for fold in {1..18}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
# 		test="$(sed "${fold}q;d" ./data/2-Fold/train.txt)";  
# 		neg="$(echo ${test} | cut -d':' -f1)";   
# 		pos="$(echo ${test} | cut -d':' -f2)";  
# 		echo $dir; 
# 		echo $test; 
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=$pos --negative_test=$neg > $dir.stats;  
# 	done

# touch ./runs/2-Fold-50-train-50-test.txt
# sleep 3

# cp -r ./runs/2-Fold-50-train-50-test/ ./runs

# 	for fold in {1..18}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
# 		test="$(sed "${fold}q;d" ./data/2-Fold/test.txt)";  
# 		neg="$(echo ${test} | cut -d':' -f1)";   
# 		pos="$(echo ${test} | cut -d':' -f2)";  
# 		echo $dir; 
# 		echo $test; 
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=$pos --negative_test=$neg > $dir.stats;  
# 	done

# touch ./runs/3-Fold-33-train-66-test.txt
# sleep 3

# cp -r ./runs/3-Fold-33-train-66-test/ ./runs

# 	for fold in {1..27}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
# 		test="$(sed "${fold}q;d" ./data/3-Fold/train.txt)";  
# 		neg="$(echo ${test} | cut -d':' -f1)";   
# 		pos="$(echo ${test} | cut -d':' -f2)";  
# 		echo $dir; 
# 		echo $test; 
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=$pos --negative_test=$neg > $dir.stats;  
# 	done

# touch ./runs/3-Fold-66-train-33-test.txt
# sleep 3

# cp -r ./runs/3-Fold-66-train-33-test/ ./runs

# 	for fold in {1..27}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
# 		test="$(sed "${fold}q;d" ./data/3-Fold/test.txt)";  
# 		neg="$(echo ${test} | cut -d':' -f1)";   
# 		pos="$(echo ${test} | cut -d':' -f2)";  
# 		echo $dir; 
# 		echo $test; 
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=$pos --negative_test=$neg > $dir.stats;  
# 	done

# touch ./runs/4-Fold-25-train-75-test.txt
# sleep 3

# cp -r ./runs/4-Fold-25-train-75-test/ ./runs

# 	for fold in {1..36}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
# 		test="$(sed "${fold}q;d" ./data/4-Fold/train.txt)";  
# 		neg="$(echo ${test} | cut -d':' -f1)";   
# 		pos="$(echo ${test} | cut -d':' -f2)";  
# 		echo $dir; 
# 		echo $test; 
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=$pos --negative_test=$neg > $dir.stats;  
# 	done

# touch ./runs/1-Fold-1-train-8-test.txt
# sleep 3

# cp -r ./runs/1-Fold-1-train-8-test/ ./runs

# 	for fold in {1..1}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/train-1.txt.pos --negative_test=./data/1-Fold/train-1.txt.neg > $dir.stats;  
# 	done

touch ./runs/1-Fold-2-train-7-test.txt
sleep 3

cp -r ./runs/1-Fold-2-train-7-test/ ./runs

	for fold in {1..1}; 
	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/train-2.txt.pos --negative_test=./data/1-Fold/train-2.txt.neg > $dir.stats;  
	done

touch ./runs/1-Fold-3-train-6-test.txt
sleep 3

cp -r ./runs/1-Fold-3-train-6-test/ ./runs

	for fold in {1..1}; 
	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/train-3.txt.pos --negative_test=./data/1-Fold/train-3.txt.neg > $dir.stats;  
	done

touch ./runs/1-Fold-4-train-5-test.txt
sleep 3

cp -r ./runs/1-Fold-4-train-5-test/ ./runs

	for fold in {1..1}; 
	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/train-4.txt.pos --negative_test=./data/1-Fold/train-4.txt.neg > $dir.stats;  
	done

touch ./runs/1-Fold-5-train-4-test.txt
sleep 3

cp -r ./runs/1-Fold-5-train-4-test/ ./runs

	for fold in {1..1}; 
	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/test-4.txt.pos --negative_test=./data/1-Fold/test-4.txt.neg > $dir.stats;  
	done

touch ./runs/1-Fold-6-train-3-test.txt
sleep 3

cp -r ./runs/1-Fold-6-train-3-test/ ./runs

	for fold in {1..1}; 
	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/test-3.txt.pos --negative_test=./data/1-Fold/test-3.txt.neg > $dir.stats;  
	done

touch ./runs/1-Fold-7-train-2-test.txt
sleep 3

cp -r ./runs/1-Fold-7-train-2-test/ ./runs

	for fold in {1..1}; 
	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";  
		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/test-2.txt.pos --negative_test=./data/1-Fold/test-2.txt.neg > $dir.stats;  
	done

# touch ./runs/1-Fold-8-train-1-test.txt
# sleep 3

# cp -r ./runs/1-Fold-8-train-1-test/ ./runs

# 	for fold in {1..1}; 
# 	do dir="$(sed "${fold}q;d" ./runs/directories.txt)";   
# 		./eval.py --eval_train --checkpoint_dir=$dir --positive_test=./data/1-Fold/test-1.txt.pos --negative_test=./data/1-Fold/test-1.txt.neg > $dir.stats;  
# 	done






