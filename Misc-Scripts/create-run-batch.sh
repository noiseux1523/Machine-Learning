#!/bin/bash

where=$1

if [ "x"$2 == "x" ] ; then
   size=15
else
   size=$2
fi

if  [ ! -f $where/train.txt ] ; then
    echo "Missing training list!"
    exit

fi
ID="run-$(date +"%y%m%d%H%M%S")-$size"

cd runs

mkdir $ID

cd $ID

split -l  $size ../../$where/train.txt "$ID-"

cd ..
cd ..
exit
