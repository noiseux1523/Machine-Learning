#!/bin/bash

if [ -f ./runs/merge-stats.csv ] ; then
  rm ./runs/merge-stats.csv
fi

for f in 1-Fold-1-train-8-test 1-Fold-2-train-7-test 1-Fold-3-train-6-test 1-Fold-4-train-5-test 1-Fold-5-train-4-test 1-Fold-6-train-3-test 1-Fold-7-train-2-test 2-Fold-50-test-50-train 2-Fold-50-train-50-test 3-Fold-33-train-66-test 3-Fold-66-train-33-test 4-Fold-25-train-75-test 4-Fold-75-train-25-test 5-Fold-20-train-80-test 5-Fold-80-train-20-test 10-Fold-10-train-90-test 10-Fold-90-train-10-test
do
  echo "Processing $f"
  cd ./runs/$f
  find . -name ".stats" > stat.txt
  if [ -f stats.txt ] ; then
    rm stats.txt
  fi
  cat ./stat.txt | while read stat  
    do echo ${stat}  
    cat ${stat} >> stats.txt 
    done
  perl /Users/noiseux1523/cnn-text-classification-tf-w2v/generate-csv-files.pl --f stats.txt > stats.csv
  cd ..
  echo "$f" >> merge-stats.csv
  cat ./$f/stats.csv >> merge-stats.csv
  echo " " >> merge-stats.csv
  cd ..
done

