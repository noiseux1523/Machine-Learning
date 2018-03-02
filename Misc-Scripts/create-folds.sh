#!/bin/bash

# ******************************************************************
# All in one script that create folds, 
#
# Arguments: 
#	1) File with the list of systems to process (based on the name of the folder with the comment patterns in encoded-comment-files)
#	2) Number of folds desired
#
# Example : 
#	bash run-all.sh systems.txt 10
#
# Folders :
#	encoded-comment-files -> Contains all the system folders, which contains the comment patterns
#	data -> Data folder containing data for each runs in automatically generated unique folders (unique ID + number of folds)
#
#			A fold is divided in 4 files, here is an example for 10-fold cross validation :
#			 	{Fold#}-test-folded-{System}.txt.neg  : Test set for non-SATD patterns (10% of all non-SATD patterns)
# 				{Fold#}-test-folded-{System}.txt.pos  : Test set for SATD patterns (10% of all SATD patterns)
# 				{Fold#}-train-folded-{System}.txt.neg : Train set for non-SATD patterns (90% of all non-SATD patterns)
# 				{Fold#}-train-folded-{System}.txt.pos : Train set for SATD patterns (90% of all SATD patterns)
#			So, for 9 systems and a 10-fold cross validation we will have :
#				(9 systems) * (10 folds) * (4 files) = 360 files combined for training and testing
#
# 	
#			
#
# ******************************************************************

# Arguments
Folds=$1   # Argument 2 -> Number of folds

#
# IMPORTANT!
# Parameters for the train script (train.py) are hardcoded in this script
# To modify them, change them directly here
# 

# Move to folder with systems comments


# Verification
if [ ! -d yes-and-no ] ; then
    echo "Missing: yes-and-no"
    exit
fi

cd ./yes-and-no


# Verification
if [ -f train.txt ] ; then
  rm train.txt
fi

# Create the folds for each system
find . -maxdepth 1 -name "folded*" -exec basename {} \; | while read patterns; 
	do cat $patterns | while read line;
		do echo "Creating folds for : $patterns";
		pos="$(echo ${line} | cut -d':' -f1)"; 
		neg="$(echo ${line} | cut -d':' -f2)"; 
		./generate-pos-neg-files-v2.pl --p $pos --n $neg --foldnb $Folds >> train.txt
	done;
done

# Copy all relevant files to data folder
ID="$(date +"%y%m%d%H%M%S")"
FolderName="$ID-$Folds-Folds"
mkdir ../data/$FolderName
echo "Copying all files to data folder : $FolderName"
cp $(find . -maxdepth 1 -name "*.csv.*" -exec basename {} \;) ../data/$FolderName
rm $(find . -maxdepth 1 -name "*.csv.*" -exec basename {} \;)
cp train.txt ../data/$FolderName/train-tmp.txt
rm train.txt

# Fix train.txt and test.txt file
echo "Preparing train and test files..."
cd ../data/$FolderName
cat train-tmp.txt | while read line;
	do neg="$(echo ${line} | cut -d':' -f1)"; 
	pos="$(echo ${line} | cut -d':' -f2)"; 
	echo "./data/$FolderName/$neg:./data/$FolderName/$pos" >> train.txt
	echo "./data/$FolderName/$neg:./data/$FolderName/$pos" >> test.txt
done
sed -i '.bak' 's/train/test/g' test.txt
rm train-tmp.txt
rm test.txt.bak
cd ../..

exit










