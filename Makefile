all:
	javac MulticastSocketAgent.java

run:
	java -Djava.net.preferIPv4Stack=true MulticastSocketAgent
