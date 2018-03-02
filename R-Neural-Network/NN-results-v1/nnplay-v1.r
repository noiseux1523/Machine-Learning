##                                        
## must have the next two
## install.packages("neuralnet")
## install.packages('caTools')
##
## nice and optional
##
## install.packages("plyr")
## install.packages('ISLR')
##
## to play with rnn
##
##install.packages('rnn')
#


library(neuralnet)
library(caTools)




data.path <- "Desktop/Neural-Network/dataset/data"
in.file <-"metrics-smells-psatd-msatd-argoUML-034.csv"
# in.file <-"metrics-smells-psatd-msatd-sql12.csv"
# in.file <-"metrics-smells-psatd-msatd-apache-jmeter-2.10.csv"
# in.file <-"metrics-smells-psatd-msatd-apache-ant-1.7.0.csv:"
# in.file <-"metrics-smells-psatd-msatd-jruby-1.4.0.csv"
# in.file <-"metrics-smells-psatd-msatd-jfreechart-1.0.19.csv"
# in.file <-"metrics-smells-psatd-msatd-jEdit.csv"
# in.file <-"metrics-smells-psatd-msatd-hibernate-distribution-3.3.2.GA.csv"
# in.file <-"metrics-smells-psatd-msatd-columba-1.4-src.csv"

d <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")

##
## remove predicted variables, strings and metrics leading to NaN
##

ds <- subset (d, select =
                     -c (Entities,RULE,Entity,File,Class,MSATDTag,
                         MSATDNum,NOPM, NOTC,CP, DCAEC, ACAIC,DCMEC,EIC,
                         EIP,PP,REIP,RRFP,RRTP,USELESS, RFP,RTP,CBOin,FanOut, CLD,NOC,NOD , NCP))


##
## use this as variable to predict
##
yes <- as.numeric(d$MSATDNum)

##
## ge the array of max and min
##
maxs <- apply(ds, 2, max)
mins <- apply(ds, 2, min)
##
## normalize input in [-1,1]
##
scaled.data <- as.data.frame(scale(ds,center = mins, scale = maxs - mins))
##
## check no NaN is there !!!
##
scaled.data[1,]

##
## add variable to predict
##
data = cbind(yes,scaled.data)

##
## set seed of really needed 
## set.seed(777)

##
## create training and test set
##
split = sample.split(data$yes, SplitRatio = 0.70)

# Split based off of split Boolean Vector
train = subset(data, split == TRUE)
test = subset(data, split == FALSE)

## get out names and use to build the formulae

feats <- names(scaled.data)

# Concatenate strings
f <- paste(feats,collapse=' + ')
##
## add the variable to predict
##
f <- paste('yes ~',f)
##
# Convert to formula
f <- as.formula(f)

print (f)



##
## train the neural network
##
## notice the hidden vectors tell how many layers and node per layers
## thus hidden=c(20,15,10) is a 3-layers where layers have 20, 15 and 10
## nodes respectively
##

## there is a randomness in training to get the same results between
## runs we must have the same seed !
##
set.seed(137)
                                        
## learn 3 network and average results basically tru to play with ensample learning ...

nn1.config=c(40,40,20,10)

nn1.config=c(40,20,10)
nn2.config=c(40,20,20)
nn3.config=c(10,10,10)

nn1 <- neuralnet(f,train,hidden=nn1.config,
                learningrate = 0.0001,
                #threshold = 0.5
                stepmax = 5e7,
                linear.output=FALSE)

nn2 <- neuralnet(f,train,hidden=nn2.config,
                learningrate = 0.0001,
                #threshold = 0.5
                stepmax = 5e7,
linear.output=FALSE)

nn3 <- neuralnet(f,train,hidden=nn3.config,
                learningrate = 0.0001,
                #threshold = 0.5
                stepmax = 5e7,
                linear.output=FALSE)


## make sure visually we have the right dimensions ...
dim(train)

predicted.nn1.values <- compute(nn1,test[2:53])

predicted.nn2.values <- compute(nn2,test[2:53])

predicted.nn3.values <- compute(nn3,test[2:53])

## round to 0,1

predicted.nn1.values$net.result <- sapply(predicted.nn1.values$net.result,round,digits=0)

predicted.nn2.values$net.result <- sapply(predicted.nn2.values$net.result,round,digits=0)

predicted.nn3.values$net.result <- sapply(predicted.nn3.values$net.result,round,digits=0)


## build the ensable score

predicted.nn.values <- predicted.nn1.values$net.result +
    predicted.nn2.values$net.result +
    predicted.nn3.values$net.result 


##
## make a bet if it is greated than zero say it was one I mean if just one net say yes
## hope it is really yes ...
##

predicted.nn.values <- (predicted.nn.values /3)
# predicted.nn.values <- sapply(predicted.nn.values,round,digits=0)
predicted.nn.values <- sapply(predicted.nn.values, function(x) ifelse  (x>0, 1,0 ))


##
## uild and inspect the 3 confusion matrices
## 
confusion.mat2.nn1 <- table(test$yes,predicted.nn1.values$net.result)
confusion.mat2.nn2 <- table(test$yes,predicted.nn2.values$net.result)
confusion.mat2.nn3 <- table(test$yes,predicted.nn3.values$net.result)

confusion.mat2.nn1
confusion.mat2.nn2
confusion.mat2.nn3

##
## build the ensable confusion matrix
##

confusion.mat2 <- table(test$yes,predicted.nn.values)

print(confusion.mat2 )

## basic accuracy measures ...

recall<- confusion.mat2[2,2]/(confusion.mat2[2,2]+confusion.mat2[2,1])
precision <-  confusion.mat2[2,2]/(confusion.mat2[2,2] +confusion.mat2[1,2])
F2 <- 2 * precision *recall /(recall + precision)

print(paste("Precision: ", precision, "Recall: ", recall, "F2: ", F2, sep=" "))
