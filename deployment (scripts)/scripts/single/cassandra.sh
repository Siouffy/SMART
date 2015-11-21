#ImageName: rugdsdev/env:cassandra-2.1.5-1-latest
#Cassandra Nodes IP on the Weave network: 10.0.0.31, 10.0.0.32, 10.0.0.33
#Cassandra Seed Nodes: 10.0.0.31, 10.0.0.32

weave run 10.0.0.31/24 -p 9042:9042 -e IP=10.0.0.31 -e SEEDS=10.0.0.31,10.0.0.32 rugdsdev/env:cassandra-2.1.5-1-latest
weave run 10.0.0.32/24 -p 9042:9042 -e IP=10.0.0.32 -e SEEDS=10.0.0.31,10.0.0.32 rugdsdev/env:cassandra-2.1.5-1-latest
weave run 10.0.0.33/24 -p 9042:9042 -e IP=10.0.0.33 -e SEEDS=10.0.0.31,10.0.0.32 rugdsdev/env:cassandra-2.1.5-1-latest
