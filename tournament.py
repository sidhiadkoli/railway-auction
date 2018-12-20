import subprocess #, os, sys

players = ['g1', 'g2', 'g3', 'g4', 'g5', 'g6', 'g7', 'g8']

err = open("err.log", "w")

for i in range(3,8):
    groups = list(players)
    excl = groups[i]
    del groups[i]

    for m in ['g1', 'g2', 'g3', 'g4', 'g5', 'g6', 'g7', 'g8', 'sa', 'fjk', 'kr']:
        print(excl + " " + m)
        out = open("railway-results/excluded_" + excl + "_map_" + m, "w")
        subprocess.run(["java", "railway.sim.Simulator", "-m", m, "-t", str(10),
                        "-p"] + groups, stdout=out, stderr=err)
        out.close()

err.close()
