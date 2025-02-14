.PHONY: server client test compile

compile:
	javac -cp ".:lib/*" *.java ServerException/*.java

server: compile
	java SocialServer

client: compile
	java SocialClient

test: compile
	java -cp ".:lib/*" org.junit.runner.JUnitCore SocialServerTest SocialClientTest
