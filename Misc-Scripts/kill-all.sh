#!/bin/bash



for i in ` screen -ls| grep manch | cut -d'(' -f1 `
do

    echo $i
    screen -X -S $i quit
    
done
exit
