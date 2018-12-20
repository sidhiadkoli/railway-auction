#!/bin/sh

for i in 0 1 ; do
    m=g1
    #for m in g2 g3 g4 g5 g6 g7 g8 sa fjk kr ; do
        echo $m $i ;
        unbuffer java railway.sim.Simulator -m $m -p g1 g2 g3 g4 g5 g6 g7 g8 -t 10 > results/map_${m}_${i} ;
    #done
done
