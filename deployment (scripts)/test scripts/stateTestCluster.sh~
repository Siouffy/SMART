weave launch
weave expose 10.0.0.1/24

#creates a local cassandra cluster of a single node and a local lightning server for SmartState Testing

weave run 10.0.0.31/24 -p 9042:9042 -e IP=10.0.0.31 rugdsdev/env:cassandra-2.1.5-1-latest

weave run 10.0.0.41/24 -p 3000:3000 deardooley/lightning-viz
