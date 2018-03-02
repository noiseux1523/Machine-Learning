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
## install.packages('rnn')
##

library(neuralnet)
library(caTools)

# data.path <- "Desktop/Neural-Network/dataset/data"
# in.file <-"metrics-smells-psatd-msatd-argoUML-034.csv"
# in.file <-"metrics-smells-psatd-msatd-sql12.csv"
# in.file <-"metrics-smells-psatd-msatd-apache-jmeter-2.10.csv"
# in.file <-"metrics-smells-psatd-msatd-apache-ant-1.7.0.csv"
# in.file <-"metrics-smells-psatd-msatd-jruby-1.4.0.csv"
# in.file <-"metrics-smells-psatd-msatd-jfreechart-1.0.19.csv"
# in.file <-"metrics-smells-psatd-msatd-jEdit.csv"
# in.file <-"metrics-smells-psatd-msatd-hibernate-distribution-3.3.2.GA.csv"
# in.file <-"metrics-smells-psatd-msatd-columba-1.4-src.csv"
# 
# paste(data.path,in.file,sep="/")
# d <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
# 
# ## remove predicted variables, strings and metrics leading to NaN
# ds <- subset (d, select = -c (Entities,RULE,Entity,File,Class,MSATDTag,
#                               MSATDNum,NOPM, NOTC,CP, DCAEC, ACAIC,DCMEC,EIC,
#                               EIP,PP,REIP,RRFP,RRTP,USELESS, RFP,RTP,CBOin,FanOut, CLD,NOC,NOD , NCP))
# 
# ## use this as variable to predict
# yes <- as.numeric(d$MSATDNum)
# 
# ## get the array of max and min
# maxs <- apply(ds, 2, max)
# mins <- apply(ds, 2, min)
# 
# ## normalize input in [-1,1]
# scaled.data <- as.data.frame(scale(ds,center = mins, scale = maxs - mins))
# 
# ## check no NaN is there !!!
# scaled.data[1,]
# 
# ## add variable to predict
# data = cbind(yes,scaled.data)
# 
# ## set seed of really needed 
# ## set.seed(777)
# 
# ## create training and test set
# split = sample.split(data$yes, SplitRatio = 0.70)
# 
# ## Split based off of split Boolean Vector
# train = subset(data, split == TRUE)
# test = subset(data, split == FALSE)
# 
# ## get out names and use to build the formulae
# feats <- names(scaled.data)
# 
# ## Concatenate strings
# f <- paste(feats,collapse=' + ')
# 
# ## add the variable to predict
# f <- paste('yes ~',f)
# 
# # Convert to formula
# f <- as.formula(f)
# print (f)



##
## To loop over te 9 systems !!
##

data.path <- "Desktop/Neural-Network/R-Neural-Network/data"
systems <- c("metrics-smells-psatd-msatd-apache-ant-1.7.0.csv",
             "metrics-smells-psatd-msatd-argoUML-034.csv",
             "metrics-smells-psatd-msatd-apache-jmeter-2.10.csv",
             "metrics-smells-psatd-msatd-jruby-1.4.0.csv",
             "metrics-smells-psatd-msatd-sql12.csv",
             "metrics-smells-psatd-msatd-jfreechart-1.0.19.csv",
             "metrics-smells-psatd-msatd-jEdit.csv",
             "metrics-smells-psatd-msatd-hibernate-distribution-3.3.2.GA.csv",
             "metrics-smells-psatd-msatd-columba-1.4-src.csv" ,
             "metrics-smells-psatd-msatd-apache-jmeter-2.10.csv")

# Loop for the number of systems
for (s in systems) {
  print(s)
  in.file <- s
  d <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
  
  ## Remove predicted variables, strings and metrics leading to NaN
  ds <- subset (d, select = -c (Entities,RULE,Entity,File,Class,MSATDTag,
                                MSATDNum,NOPM, NOTC,CP, DCAEC, ACAIC,DCMEC,EIC,
                                EIP,PP,REIP,RRFP,RRTP,USELESS, RFP,RTP,CBOin,FanOut, CLD,NOC,NOD , NCP))
  
  ## Use this as variable to predict
  yes <- as.numeric(d$MSATDNum)
  
  ## Get the array of max and min
  maxs <- apply(ds, 2, max)
  mins <- apply(ds, 2, min)
  
  ## Normalize input in [-1,1]
  scaled.data <- as.data.frame(scale(ds,center = mins, scale = maxs - mins))
  
  ## Check no NaN is there !!!
  scaled.data[1,]
  
  ## Add variable to predict
  data = cbind(yes,scaled.data)
  
  ## Get out names and use to build the formulae
  feats <- names(scaled.data)
  
  ## Concatenate strings
  f <- paste(feats,collapse=' + ')
  
  ## Add the variable to predict
  f <- paste('yes ~',f)
  
  ## Convert to formula
  f <- as.formula(f)
  
  ## Initialize variable to store scores
  score <- c()
  
  ##
  ## HARDCODED: Loop the number of repetition
  ##
  for (i in c(1:20)) {
    print(i)
    
    ## create training and test set
    # split = sample.split(data$yes, SplitRatio = 0.70)
    
    ## Split based off of split Boolean Vector
    # train = subset(data, split == TRUE)
    # test = subset(data, split == FALSE)
    
    #Randomly shuffle the data
    data_shuffle<-data[sample(nrow(data)),]
    
    #Create 10 equally size folds
    folds <- cut(seq(1,nrow(data_shuffle)),breaks=10,labels=FALSE)
    
    #Prediction array
    d <- dim(data_shuffle)[1]
    p.vect <- array(0,d)
    
    ##
    ## HARDCODED: Loop for the number of folds
    ##
    for (k in c(1:10)) {
      ## Segment your data by fold using the which() function 
      testIndexes <- which(folds==k,arr.ind=TRUE)
      test <- data_shuffle[testIndexes, ]
      train <- data_shuffle[-testIndexes, ]
      
      ##
      ## train the neural network
      ##
      ## notice the hidden vectors tell how many layers and node per layers
      ## thus hidden=c(20,15,10) is a 3-layers where layers have 20, 15 and 10
      ## nodes respectively
      ##
      
      ## there is a randomness in training to get the same results between
      ## runs we must have the same seed !
      ## set.seed(137)
      
      ## learn 3 network and average results basically tru to play with ensample learning ...
      nn1.config=c(40,20)
      #nn2.config=c(40,20,20)
      #nn3.config=c(10,10,10)
      
      nn1 <- neuralnet(f,train,hidden=nn1.config,
                       learningrate = 0.0001,
                       #threshold = 0.5
                       stepmax = 5e7,
                       linear.output=FALSE)
      
      # nn2 <- neuralnet(f,train,hidden=nn2.config,
      #                  learningrate = 0.0001,
      #                  #threshold = 0.5
      #                  stepmax = 5e7,
      #                  linear.output=FALSE)
      
      # nn3 <- neuralnet(f,train,hidden=nn3.config,
      #                  learningrate = 0.0001,
      #                  #threshold = 0.5
      #                  stepmax = 5e7,
      #                  linear.output=FALSE)
      
      ## make sure visually we have the right dimensions ...
      predicted.nn1.values <- compute(nn1,test[2:53])
      # predicted.nn2.values <- compute(nn2,test[2:53])
      # predicted.nn3.values <- compute(nn3,test[2:53])
      
      ## round to 0,1
      predicted.nn1.values$net.result <- sapply(predicted.nn1.values$net.result,round,digits=0)
      # predicted.nn2.values$net.result <- sapply(predicted.nn2.values$net.result,round,digits=0)
      # predicted.nn3.values$net.result <- sapply(predicted.nn3.values$net.result,round,digits=0)
      
      index <- 1
      for(j in as.numeric(rownames(test))) {
        p.vect[j] <- predicted.nn1.values$net.result[index]
        index <- index + 1
      }
    }
    ## make a bet if it is greated than zero say it was one I mean if just one net say yes
    ## hope it is really yes ...
    ## HARDCODED: The division is made based on the number of folds
    # predicted.nn.values <- (predicted.nn.values /10)
    # predicted.nn.values <- sapply(predicted.nn.values, function(x) ifelse  (x>0.5, 1,0 ))
    # predicted.nn.values <- sapply(predicted.nn.values, function(x) ifelse  (x>0, 1,0))
    
    ## uild and inspect the 3 confusion matrices
    # confusion.mat2.nn1 <- table(test$yes,predicted.nn1.values$net.result)
    # confusion.mat2.nn2 <- table(test$yes,predicted.nn2.values$net.result)
    # confusion.mat2.nn3 <- table(test$yes,predicted.nn3.values$net.result)
    
    # confusion.mat2.nn1
    # confusion.mat2.nn2
    # confusion.mat2.nn3
    
    ## build the ensable confusion matrix
    confusion.mat <- table(data$yes,p.vect)
    # print(confusion.mat2 )
    
    ## basic accuracy measures ...
    recall      <- confusion.mat[2,2]/(confusion.mat[2,2]+confusion.mat[2,1])
    precision   <- confusion.mat[2,2]/(confusion.mat[2,2]+confusion.mat[1,2])
    specificity <- confusion.mat[1,1]/(confusion.mat[1,1]+confusion.mat[1,2])
    F1          <- 2*confusion.mat[2,2]/(2*confusion.mat[2,2]+confusion.mat[1,2]+confusion.mat[2,1])
    F2          <- 2*precision*recall/(recall+precision)
    
    ## HARDCODED: Division based on the number of k-folds
    print(paste("Precision: ",   precision, 
                "Recall: ",      recall, 
                "Specificity: ", specificity,
                "F1: ",          F1, 
                "F2: ",          F2, 
                sep=" "))
    score <- rbind(score, c(recall, precision, specificity, F1, F2))
  }
  ## build file name
  storename <- gsub("metrics-smells-psatd-msatd-","NN-results-",s)
  
  ## bind column names ...
  colnames(score) <-  c("recall", "precision", "specificity", "F1","F2")
  
  ## save data ...
  write.table(format(score, scientific=FALSE),
              col.names = T, 
              row.names = F, 
              file = paste("Desktop/Neural-Network/R-Neural-Network", storename, sep="/"), sep=";")
}
