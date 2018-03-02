# install.packages("caret", dependencies = c("Depends", "Suggests"))
library(caret)
library(mlbench)
library(lattice)
library(pls)
library(klaR)
library(neuralnet)
library(nnet)

# Read data file
d <- read.csv(file="Desktop/Neural-Network/R-Neural-Network/new-data/metrics-smells-psatd-msatd-apache-ant-1.7.0.csv", header = T, sep = ";")

# Remove Maybe SATD
dr <- subset(d, MSATDNum != 1)
dr$MSATDNum <- sapply(dr$MSATDNum, function(x) ifelse  (x==2, 1,0 ))

## Remove predicted variables, strings and metrics leading to NaN
ds <- subset (dr, select = -c (Entities, Entity, File, Class, MSATDTag,
                              MSATDNum, NOPM, NOTC, CP, DCAEC, ACAIC, DCMEC, EIC,
                              EIP, PP, REIP, RRFP, RRTP, USELESS, RFP, RTP, CBOin, 
                              FanOut, CLD, NOC, NOD, NCP, paragraphs, interrogative, 
                              article, subordination, Tot, D, B, M, I, T, W, C))

## Use this as variable to predict
yes <- as.numeric(dr$MSATDNum)

## Get the array of max and min
maxs <- apply(ds, 2, max)
mins <- apply(ds, 2, min)

## Normalize input in [-1,1]
scaled.data <- as.data.frame(scale(ds,center = mins, scale = maxs - mins))

## Check no NaN is there !!!
scaled.data[1,]

## Add variable to predict
data = cbind(yes,scaled.data)

# Create data partition for training and testing
set.seed(107)
inTrain <- createDataPartition(y = data$yes,
                               ##y = samples.m,  ## The outcome data are needed
                               p = .75,          ## The percentage of data in the training set
                               list = FALSE)     ## The format of the results

# Show the two partitions
str(inTrain)
training <- data[ inTrain,]
testing  <- data[-inTrain,]
nrow(training)
nrow(testing)

## NEURAL NETWORK
ctrl <- trainControl(method = "repeatedcv", repeats = 5, number = 10, classProbs = TRUE)
ff   <- train(data[,1:length(data)-1], data[,length(data)],
              method = "nnet",
              MaxNWts = 2000,
              metric = "ROC",
              preProcess = c("center", "scale"),
              tuneLength = 10,
              trControl = ctrl)
ff
