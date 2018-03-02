#!/bin/bash

where=$1


if  [ ! -f $where ] ; then
    echo "Missing batch list!"
    exit

fi

for i in `cat $where`
do
	echo $i
	neg="$(echo ${i} | cut -d':' -f1)";   
	pos="$(echo ${i} | cut -d':' -f2)";
	cmd="./train.py --enable_word_embeddings true --num_epochs 6 --dev_sample_percentage 0.01 --positive_train=$pos --negative_train=$neg"
	echo $cmd
	screen -d -m  ./train.py --enable_word_embeddings true --num_epochs 6 --dev_sample_percentage 0.01 --positive_train=$pos --negative_train=$neg
	sleep 5
done
exit
