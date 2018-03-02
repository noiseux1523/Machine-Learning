#!/bin/bash

touch ./runs/8-train-1-test.txt

./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/train-1.txt.pos --negative_train=./data/1-Fold/train-1.txt.neg

touch ./runs/1-train-8-test.txt

./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/test-1.txt.pos --negative_train=./data/1-Fold/test-1.txt.neg

# touch ./runs/7-train-2-test.txt

# ./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/train-2.txt.pos --negative_train=./data/1-Fold/train-2.txt.neg

# touch ./runs/2-train-7-test.txt

# ./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/test-2.txt.pos --negative_train=./data/1-Fold/test-2.txt.neg

# touch ./runs/6-train-3-test.txt

# ./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/train-3.txt.pos --negative_train=./data/1-Fold/train-3.txt.neg

# touch ./runs/3-train-6-test.txt

# ./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/test-3.txt.pos --negative_train=./data/1-Fold/test-3.txt.neg

# touch ./runs/5-train-4-test.txt

# ./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/train-4.txt.pos --negative_train=./data/1-Fold/train-4.txt.neg

# touch ./runs/4-train-5-test.txt

# ./train.py --enable_word_embeddings false --num_epochs 10 --positive_train=./data/1-Fold/test-4.txt.pos --negative_train=./data/1-Fold/test-4.txt.neg