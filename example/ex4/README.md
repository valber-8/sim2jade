watertank controled by the JADE agent through the Redis

javac -classpath <PATH TO JADE>/lib/jade.jar:jedis.jar PIDAgent.java

java -cp <PATH TO JADE>/lib/jade.jar:jedis.jar:. jade.Boot -gui -agents pid:PIDAgent
