#uploadStreamDemo

This repo demonstrates the usage of Akka (in Scala) with Google Cloud PubSub and Storage. It consists of two applications which are configured in build.sbt. 

The high-level view of the project is that a client application publishes a message to PubSub with a deviceId and a groupId. Then, a service which is subscribed to the published messages passes this message to a device in an Akka Actor system. The corresponding device then uploads data to Google Cloud Storage.

# Running the applications

## pub-client

From the sbt console, run

```$xslt
pub/run
```

This client is used to publish messages to Google Cloud PubSub. It prompts the user for a topic to publish to, and then the user can publish messages to that topic. For now, the message consists of a simple _deviceId_ and _groupId_ representing a device in an IOT System (represented in the sub-service).

## sub-service

### Waiting for Alpakka release
There is currently a compatibility issue between Google Cloud Storage and PubSub in that they use incompatible jwt libraries. Instead of looking for a workaround, I am waiting for the next Alpakka release.

From the sbt console, run

```$xslt
sub/run
```

The sub-service is an Akka Actor System representing a group of devices, similar to that in the [Akka Getting Started Guide](https://doc.akka.io/docs/akka/current/guide/index.html). 

_details are a work in progress_

# Configuration
This application requires an active Google Cloud Platform project and API key. Links to instructions are included below.

[Creating a Project](https://cloud.google.com/resource-manager/docs/creating-managing-projects)

[Creating an API Key](https://cloud.google.com/docs/authentication/api-keys)

You will then need to store your downloaded API key as an environment variable on your system called GOOGLE_CLOUD_KEY. This environment variable is retrieved in the application.conf file for both applications. You may use a different name for your environment variable by configuring the name in application.conf for each sub-project.