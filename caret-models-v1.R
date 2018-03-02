# load libraries
library(caret)
library(pROC)
library(DMwR)
set.seed(1234)

# configure multicore
#library(doMC)
#registerDoMC(cores=4)

# gbm glm nnet bdk xyf treebag nb lda lda2 fda LogitBoost prf
      
      #################################################
      # data prep
      #################################################
      
      # load data
      d <- read.csv('Desktop/Neural-Network/R-Neural-Network/new-data/metrics-smells-psatd-msatd-argoUML-034.csv', header = T, sep=';')
      #titanicDF$Title <- ifelse(grepl('Mr ',titanicDF$Name),'Mr',ifelse(grepl('Mrs ',titanicDF$Name),'Mrs',ifelse(grepl('Miss',titanicDF$Name),'Miss','Nothing'))) 
      #titanicDF$Age[is.na(titanicDF$Age)] <- median(titanicDF$Age, na.rm=T)
      
      # miso format
      #titanicDF <- titanicDF[c('PClass', 'Age',    'Sex',   'Title', 'Survived')]
      
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
      #print(names(data))
      
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
      # names(getModelInfo())
      
      data$SATD <- ifelse(data$SATD==1,'yes','no')
      
      # pick model gbm and find out what type of model it is
      # getModelInfo()$gbm$type
      
      # split data into training and testing chunks
      splitIndex <- createDataPartition(data[,outcomeName], p = .50, list = FALSE, times = 1)
      trainDF <- data[ splitIndex,]
      testDF  <- data[-splitIndex,]
      
      # create caret trainControl object to control the number of cross-validations performed
      # objControl <- trainControl(method='repeatedcv',
      #                            number=10,
      #                            repeats=3,
      #                            returnResamp='none',
      #                            summaryFunction = twoClassSummary,
      #                            classProbs = TRUE
      #                            )
      
      # run model
      # objModel <- train(trainDF[,predictorsNames], as.factor(trainDF[,outcomeName]),
      #                   method='treebag', 
      #                   trControl=objControl,  
      #                   metric = "ROC",
      #                   tuneLength = 5,
      #                   preProc = c("center", "scale"))
      
      
      # find out variable importance
      # summary(objModel)
      
      # find out model details
      # objModel
      
      # SMOTE
      trainDF$SATD <- as.factor(trainDF$SATD)
      trainDF <- SMOTE(SATD ~ ., trainDF, perc.over = 100, perc.under=200)
      #trainDF$SATD <- as.numeric(trainDF$SATD)
      
      objControl <- trainControl(method='repeatedcv', 
                                 number=10, 
                                 repeats=3, 
                                 returnResamp='none', 
                                 summaryFunction = twoClassSummary, 
                                 classProbs = TRUE
      )
      
      objModel <- train(SATD ~ ., data = trainDF, method = "rf",
                       trControl = objControl)
      
      objModel
      
      # predictors <- names(trainDF)[names(trainDF) != 'SATD']
      # pred <- predict(objModel$finalModel, testDF[,predictors])
      # 
      # auc <- roc(ifelse(testDF$SATD=="yes",1,0), ifelse(pred=="yes",1,0))
      # print(auc)
      # 
      # plot(auc, ylim=c(0,1), print.thres=TRUE, main=paste('AUC:',round(auc$auc[[1]],2)))
      # abline(h=1,col='blue',lwd=2)
      # abline(h=0,col='red',lwd=2)
      
      #################################################
      # evalutate model
      #################################################
      # get predictions on your testing data
      
      # class prediction
      predictions <- predict(object=objModel, testDF[,predictorsNames], type='raw')
      head(predictions)
      postResample(pred=predictions, obs=as.factor(testDF[,outcomeName]))
      
      # probabilities 
      predictions <- predict(object=objModel, testDF[,predictorsNames], type='prob')
      head(predictions)
      postResample(pred=predictions[[2]], obs=ifelse(testDF[,outcomeName]=='yes',1,0))
      auc <- roc(ifelse(testDF[,outcomeName]=="yes",1,0), predictions[[2]])
      print(auc$auc)
      
      # find out variable importance
      #plot.new()
      plot(varImp(objModel,scale=F), 
           main = paste("VarImp"),
           width = 0.85)
      #dev.off()
      
      # display variable importance on a +/- scale 
      plot.new()
      pdf(file = "~/Desktop/Neural-Network/R-Neural-Network/VarImp-apache.pdf",
          pointsize = 12, bg = "white")
      vimp <- varImp(objModel, scale=F)
      results <- data.frame(row.names(vimp$importance),vimp$importance$Overall)
      results$VariableName <- rownames(vimp)
      colnames(results) <- c('VariableName','Weight')
      results <- results[order(results$Weight),]
      results <- results[(results$Weight != 0),]
      
      par(mar=c(5,15,4,2)) # increase y-axis margin. 
      xx <- barplot(results$Weight, width = 0.85, 
                    main = paste0("Variable Importance -",outcomeName), horiz = T, 
                    xlab = "< (-) importance >  < neutral >  < importance (+) >", axes = FALSE, 
                    col = ifelse((results$Weight > 0), 'blue', 'red')) 
      axis(2, at=xx, labels=results$VariableName, tick=FALSE, las=2, line=-0.3, cex.axis=0.5)  
      dev.off()
      
      # Performance
      cmatr <- confusionMatrix(predict(object=objModel, testDF[,predictorsNames], type='raw'), as.factor(testDF[,outcomeName]), positive = "yes")
      cmatr
      
      # Performance values
      recall      <- cmatr$table[2,2]/(cmatr$table[2,2]+cmatr$table[2,1])
      precision   <- cmatr$table[2,2]/(cmatr$table[2,2]+cmatr$table[1,2])
      specificity <- cmatr$table[1,1]/(cmatr$table[1,1]+cmatr$table[1,2])
      F1          <- 2*cmatr$table[2,2]/(2*cmatr$table[2,2]+cmatr$table[1,2]+cmatr$table[2,1])
      F2          <- 2*precision*recall/(recall+precision)
      
      cat(paste("Precision:   ",   precision, 
                "\nRecall:      ",      recall, 
                "\nSpecificity: ", specificity,
                "\nF1:          ",          F1, 
                "\nF2:          ",          F2))
      
      
      
      
      
      
      