library(effsize)
library(lmPerm)
library(rpart)
library(tree)

## ARGHHHH !!!! I hard coded the prediction variable !!!!!!!!

r.cv.cart.folds<-function(formula,data,crossValFolds=10,...) {
  d <- dim(data)[1]
  dY<-data[data$MSATDTag=="Yes",] # dY will contain the data where there was a progression
  dN<-data[data$MSATDTag=="No",] # dN will contain the data where there was no progression 
  dYind<-rownames(dY)
  dNind<-rownames(dN)
  normalSizeFoldsY<-nrow(dY)%/%crossValFolds
  normalSizeFoldsN<-nrow(dN)%/%crossValFolds
  numberOfLargerFoldsY<-nrow(dY)%%crossValFolds # due to the remainder of the division
  numberOfLargerFoldsN<-nrow(dN)%%crossValFolds
  p.vect <- array(0,d)
  print(p.vect)
  
  for(i in c(1:crossValFolds)) {
    
    # Pick up the right amount of cases where there was no progression, i.e, the right amount of NO:
    if(i<=(crossValFolds-numberOfLargerFoldsN)){
      selectedIndN<-sample(as.numeric(dNind),normalSizeFoldsN)
    }else{
      selectedIndN<-sample(as.numeric(dNind),normalSizeFoldsN+1)
    }
    dNind<-setdiff(dNind,selectedIndN)
    
    
    # Pick up the right amount of cases where there was a progression, i.e, the right amount of YES:
    if(i<=(crossValFolds-numberOfLargerFoldsY)){
      selectedIndY<-as.numeric(sample(dYind,normalSizeFoldsY))
    }else{
      selectedIndY<-as.numeric(sample(dYind,normalSizeFoldsY+1))
    }
    dYind<-setdiff(dYind,selectedIndY)
    
    selectedInd<-c(selectedIndN,selectedIndY)
    print(selectedInd)
    
    mlearn <- rpart(formula,data=data[-selectedInd,],method = 'class')
    for(j in selectedInd) {
      p.vect[j]<-predict(mlearn,newdata=data[j,],type="class")
    }
    print(p.vect)
  }
  
  return(factor(p.vect, levels = 1:2, labels = c("No", "Yes")))
}



### single file experiment

data.path <- "Desktop/Neural-Network/dataset/data"

in.file <-"metrics-smells-psatd-msatd-apache-ant-1.7.0.csv" # very very tough lower than 10%
in.file <-"metrics-smells-psatd-msatd-argoUML-034.csv"
in.file <-"metrics-smells-psatd-msatd-apache-jmeter-2.10.csv"
in.file <-"metrics-smells-psatd-msatd-jruby-1.4.0.csv"
in.file <-"metrics-smells-psatd-msatd-sql12.csv"
in.file <-"metrics-smells-psatd-msatd-jfreechart-1.0.19.csv" # medium tough 18%
in.file <-"metrics-smells-psatd-msatd-jEdit.csv" # medium tough 12%
in.file <-"metrics-smells-psatd-msatd-hibernate-distribution-3.3.2.GA.csv"
in.file <-"metrics-smells-psatd-msatd-columba-1.4-src.csv" # this is very very tough lower than 10%
in.file <-"metrics-smells-psatd-msatd-apache-jmeter-2.10.csv"


## manual experiments ...

## cfit <- rpart(MSATDTag ~ . , data = ds, method = 'class')

## ctree <- tree(MSATDTag  ~ . , data = ds)
## res <- predict(ctree, type=c("class"),ds)
## res.mat <- table(res,ds$MSATDTag)
## res.mat
## sum(diag(table(res,ds$MSATDTag)))/length(ds$MSATDTag)


###
## to loop over te 9 systems !!
###

systems <- c(#"metrics-smells-psatd-msatd-apache-ant-1.7.0.csv",
             #"metrics-smells-psatd-msatd-argoUML-034.csv",
             #"metrics-smells-psatd-msatd-apache-jmeter-2.10.csv",
             #"metrics-smells-psatd-msatd-jruby-1.4.0.csv",
             #"metrics-smells-psatd-msatd-sql12.csv",
             #"metrics-smells-psatd-msatd-jfreechart-1.0.19.csv",
             #"metrics-smells-psatd-msatd-jEdit.csv",
             #"metrics-smells-psatd-msatd-hibernate-distribution-3.3.2.GA.csv",
             #"metrics-smells-psatd-msatd-columba-1.4-src.csv" ,
             "metrics-smells-psatd-msatd-apache-jmeter-2.10.csv")



## to compte cross validation via RPART over the 9 systems  for 20 times

for (s in systems){
  in.file <- s
  
  d <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
  ds <- subset (d, select = -c (Entities,RULE,Entity,File,Class,MSATDNum))
  
  
  
  score <- c()
  
  
  ## number of repetitions !!! HARD CODED
  
  for (i in c(1:20)){
    res.cv<-r.cv.cart.folds(formula="MSATDTag ~ . ",data = ds)
    confusion.mat2<-table(ds$MSATDTag,res.cv)
    confusion.mat2
    error<-1-sum(diag(confusion.mat2))/sum(confusion.mat2)
    error
    
    NegValues <- confusion.mat2[1,1]+confusion.mat2[1,2]
    PosValues <- confusion.mat2[2,1]+confusion.mat2[2,2]
    
    recall<- confusion.mat2[2,2]/(confusion.mat2[2,2]+confusion.mat2[2,1])
    
    specificity <- confusion.mat2[1,1]/(confusion.mat2[1,1]+confusion.mat2[1,2])
    
    
    precision <-  confusion.mat2[2,2]/(confusion.mat2[2,2] +confusion.mat2[1,2])
    
    F2 <- 2 * precision *recall /(recall + precision)
    sensitivity = 0
    falseNegativesRate <- 1-sensitivity
    
    
    F1 <- 2*confusion.mat2[2,2]/(2*confusion.mat2[2,2]+confusion.mat2[1,2]+confusion.mat2[2,1])
    
    ## store data to save later on
    score <-  rbind(score, c(recall, precision, specificity, F1, F2,  confusion.mat2[1,1],confusion.mat2[1,2],confusion.mat2[2,1], confusion.mat2[2,2]))
    
  } ## loop over i
  
  
  ## build file name
  
  storename <- gsub("metrics-smells-psatd-msatd-","results-",s)
  
  ## bind column names ...
  
  colnames(score) <-  c("recall", "precision", "specificity", "F1","F2", "confmatNN","confmatNY","confmatYN", "confmatYY")
  
  
  ## save data ...
  
  write.table(format(score, scientific=FALSE),col.names = T, row.names = F, 
              file =  paste("./", storename  , sep="/"),  sep=";")
  
  
} ## loop over system
