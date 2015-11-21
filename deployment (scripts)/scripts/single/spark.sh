#ImageName: rugdsdev/spark
#Master IP on the Weave network: 10.0.0.51
#Workers IP on the Weave network: 10.0.0.52, 10.0.0.53

weave run 10.0.0.51/24 -t -e SPARK_LOCAL_IP=10.0.0.51 rugdsdev/spark /start-master.sh
weave run 10.0.0.52/24 -t -e SPARK_LOCAL_IP=10.0.0.52 -e SPARK_MASTER_IP=10.0.0.51 rugdsdev/spark /start-worker.sh
weave run 10.0.0.53/24 -t -e SPARK_LOCAL_IP=10.0.0.53 -e SPARK_MASTER_IP=10.0.0.51 rugdsdev/spark /start-worker.sh




