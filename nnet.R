library(nnet)
data.path <- "~/Desktop/Neural-Network"
system <- "train-set-delta-caret.csv"
d <- read.csv(paste(data.path,system,sep="/"), header = T, sep = ";")
a = nnet(Class~., data=d,size=3,maxit=10000,decay=.1)