compile:
	javac railway/sim/utils/*.java
	javac railway/sim/*.java

clean:
	rm railway/*/*.class

run:
	java railway.sim.Simulator -m g2 -p g1 g2 g3 g4 g5 g6 g7 g8

map:
	java railway.sim.Simulator --gui -m sim

gui:
	java railway.sim.Simulator -m g2 --gui --fps 1 -p g1 g2 g3 g4 g5 g6 g7 g8

random:
	java railway.sim.Simulator -p random random random --gui --fps 1
