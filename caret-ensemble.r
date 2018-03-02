library("caret")
library("mlbench")
library("pROC")
library("rpart")
library("caretEnsemble")
library("mlbench")
library("randomForest")
library("nnet")
library("caTools")
library("devtools")
#install.packages("nnls")
#install.packages("mboost")

# load data
d <- read.csv('Desktop/Neural-Network/R-Neural-Network/new-data/metrics-smells-psatd-msatd-apache-ant-1.7.0.csv', header = T, sep=';')

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
SATD <- as.numeric(dr$MSATDNum)

## Get the array of max and min
maxs <- apply(ds, 2, max)
mins <- apply(ds, 2, min)

## Normalize input in [-1,1]
scaled.data <- as.data.frame(scale(ds,center = mins, scale = maxs - mins))

## Check no NaN is there !!!
scaled.data[1,]

## Add variable to predict
data = cbind(SATD,scaled.data)

# dummy variables for factors/characters
#titanicDF$Title <- as.factor(titanicDF$Title)
dataDummy <- dummyVars("~.",data=data, fullRank=F)
data <- as.data.frame(predict(dataDummy,data))
print(names(data))

# what is the proportion of your outcome variable?
prop.table(table(data$SATD))

# save the outcome for the glmnet model
tempOutcome <- data$SATD  

# generalize outcome and predictor variables
outcomeName <- 'SATD'
predictorsNames <- names(data)[names(data) != outcomeName]

#################################################
# model it
#################################################
# get names of all caret supported models 
names(getModelInfo())

data$SATD <- ifelse(data$SATD==1,'yes','no')

# pick model gbm and find out what type of model it is
getModelInfo()$gbm$type

# split data into training and testing chunks
set.seed(1234)
splitIndex <- createDataPartition(data[,outcomeName], p = .75, list = FALSE, times = 1)
trainDF <- data[ splitIndex,]
testDF  <- data[-splitIndex,]

my_control <- trainControl(method="boot",
                           number=25,
                           savePredictions="final",
                           classProbs=TRUE,
                           index=createResample(trainDF$SATD, 25),
                           summaryFunction=twoClassSummary)

model_list <- caretList(trainDF[,predictorsNames], as.factor(trainDF[,outcomeName]),
                        trControl=my_control,
                        methodList=c("gbm", "blackboost", "parRF"))

p <- as.data.frame(predict(model_list, newdata=head(testDF)))
print(p)

xyplot(resamples(model_list))
modelCor(resamples(model_list))

greedy_ensemble <- caretEnsemble(model_list, 
                                metric="ROC",
                                trControl=trainControl(number=2,
                                                       summaryFunction=twoClassSummary,
                                                       classProbs=TRUE))
summary(greedy_ensemble)

model_preds <- lapply(model_list, predict, newdata=testDF, type="prob")
model_preds <- lapply(model_preds, function(x) x[,"yes"])
model_preds <- data.frame(model_preds)
ens_preds <- predict(greedy_ensemble, newdata=testDF, type="prob")
model_preds$ensemble <- ens_preds
caTools::colAUC(model_preds, testDF$SATD)

varImp(greedy_ensemble)

glm_ensemble <- caretStack(
  model_list,
  method="glm",
  metric="ROC",
  trControl=trainControl(
    method="boot",
    number=10,
    savePredictions="final",
    classProbs=TRUE,
    summaryFunction=twoClassSummary
  )
)
model_preds2 <- model_preds
model_preds2$ensemble <- predict(glm_ensemble, newdata=testDF, type="prob")
CF <- coef(glm_ensemble$ens_model$finalModel)[-1]
colAUC(model_preds2, testDF$SATD)

CF/sum(CF)

library("gbm")
gbm_ensemble <- caretStack(
  model_list,
  method="gbm",
  verbose=FALSE,
  tuneLength=10,
  metric="ROC",
  trControl=trainControl(
    method="boot",
    number=10,
    savePredictions="final",
    classProbs=TRUE,
    summaryFunction=twoClassSummary
  )
)
model_preds3 <- model_preds
model_preds3$ensemble <- predict(gbm_ensemble, newdata=testDF, type="prob")
colAUC(model_preds3, testDF$SATD)









