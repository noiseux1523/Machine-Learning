data.path <- "Desktop/Neural-Network/R-Neural-Network"

in.file <- "NN-results-apache-ant-1.7.0.csv"
    ant  <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <- "NN-results-apache-jmeter-2.10.csv"
    jmeter <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <- "NN-results-argoUML-034.csv"
    argo <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <-  "NN-results-columba-1.4-src.csv"
    columba <-read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <- "NN-results-hibernate-distribution-3.3.2.GA.csv"
    hibernate <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <- "NN-results-jEdit.csv"
    jedit <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <- "NN-results-jfreechart-1.0.19.csv"
    jfree <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <- "NN-results-jruby-1.4.0.csv"
    jruby <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")
in.file <-"NN-results-sql12.csv" 
    sq <- read.csv(paste(data.path,in.file,sep="/"), header = T, sep = ";")



pdf(file = "~/Desktop/Neural-Network/R-Neural-Network/F2.pdf",
    #width = 480, height = 480, 
    pointsize = 12, bg = "white",
    )



boxplot(ant$F2,jmeter$F2,argo$F2,columba$F2,hibernate$F2,jedit$F2,jfree$F2,jruby$F2,sq$F2, ylab="F2",boxwex=0.15,xlab=("Application"), col=c("lightblue","lightyellow"),
        names=c("ant","jmeter","argo","colum.","hib.","jedit","jfree","jruby","sq12")
        )

title("F2 -- rpart 20 repetition of a 10-fold cross validation")

dev.off()





pdf(file = "~/Desktop/Neural-Network/R-Neural-Network/Precision.pdf",
    #width = 480, height = 480, 
    pointsize = 12, bg = "white",
    )



boxplot(ant$precision,jmeter$precision,argo$precision,columba$precision,hibernate$precision,jedit$precision,jfree$precision,jruby$precision,sq$precision, ylab="precision",boxwex=0.15,xlab=("Application"), col=c("lightblue","lightyellow"),
        names=c("ant","jmeter","argo","colum.","hib.","jedit","jfree","jruby","sq12")
        )

title("Precision -- rpart 20 repetition of a 10-fold cross validation")

dev.off()





pdf(file = "~/Desktop/Neural-Network/R-Neural-Network/Recall.pdf",
    #width = 480, height = 480, 
    pointsize = 12, bg = "white",
    )



boxplot(ant$recall,jmeter$recall,argo$recall,columba$recall,hibernate$recall,jedit$recall,jfree$recall,jruby$recall,sq$recall, ylab="recall",boxwex=0.15,xlab=("Application"), col=c("lightblue","lightyellow"),
        names=c("ant","jmeter","argo","colum.","hib.","jedit","jfree","jruby","sq12")
        )

title("Recall -- rpart 20 repetition of a 10-fold cross validation")

dev.off()




