#!/bin/bash

# ******************************************************************
# All in one script that create folds, train models and test models
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
# 	runs -> Folder containing the trained models in automatically generated unique folders (unique ID)
#			
#
# ******************************************************************

# Arguments
Systems=$1 # Argument 1 -> List of system directories
Folds=$2   # Argument 2 -> Number of folds

#
# IMPORTANT!
# Parameters for the train script (train.py) are hardcoded in this script
# To modify them, change them directly here
# 

# Move to folder with systems comments
cd ./encoded-comment-files

# Get list of all pattern files for each system
cat ../$Systems | while read sys; 
	do echo "Listing all pattern files of : $sys"
	find $sys -name "*.txt" > folded-$sys.txt
done

# Verification
if [ -f train.txt ] ; then
  rm train.txt
fi

# Create the folds for each system
find . -maxdepth 1 -name "folded*" -exec basename {} \; | while read patterns; 
	do echo "Creating folds for : $patterns"
	perl generate-pos-neg-files.pl --f $patterns --foldnb $Folds >> train.txt
done

# Copy all relevant files to data folder
ID="$(date +"%y%m%d%H%M%S")"
FolderName="$ID-$Folds-Folds"
mkdir ../data/$FolderName
echo "Copying all files to data folder : $FolderName"
cp $(find . -maxdepth 1 -name "*pattern.txt.*" -exec basename {} \;) ../data/$FolderName
rm $(find . -maxdepth 1 -name "*pattern.txt.*" -exec basename {} \;)
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

# Train on all folds of all systems
echo "Train on all folds..."
cat ./data/$FolderName/train.txt | while read train; 
	do echo "Training on ${train}..."; 
	neg="$(echo ${train} | cut -d':' -f1)"; 
	pos="$(echo ${train} | cut -d':' -f2)"; 

	# !!! FEEL FREE TO MODIFY PARAMETERS !!! #
	# Make sure to have enough epochs

	# No word custom embeddings
	# ./train.py --enable_word_embeddings false --num_epochs 6 --positive_train=$pos --negative_train=$neg

	./train.py --enable_word_embeddings true --num_epochs 6 --positive_train=$pos --negative_train=$neg

done

# Test on all folds of all systems
echo "Test on all folds..."
mkdir ./runs/$FolderName
it=1
find ./runs -maxdepth 2 -name "checkpoints" | while read dir;
	do test="$(sed "${it}q;d" ./data/$FolderName/test.txt)";  
	neg="$(echo ${test} | cut -d':' -f1)";   
	pos="$(echo ${test} | cut -d':' -f2)";  
	echo $dir; 
	echo $test; 

	./eval.py --eval_train --checkpoint_dir=$dir --positive_test=$pos --negative_test=$neg > $dir/.stats;  

	folder="${dir/\/checkpoints/ }"
	mv $folder ./runs/$FolderName
	it=$((it+1))
done

# Write results in .csv file
echo "Writing results of $FolderName..."

# Verification
if [ -f ./runs/$FolderName/stats.txt ] ; then
  rm ./runs/$FolderName/stats.txt
fi

echo "Saving text stats..."
find ./runs/$FolderName -name ".stats" | while read stat  
  do cat ${stat} >> ./runs/$FolderName/stats.txt 
done

echo "Combining all stats in csv format..."
perl ./generate-csv-files.pl --f ./runs/$FolderName/stats.txt > ./runs/$FolderName/stats.csv











