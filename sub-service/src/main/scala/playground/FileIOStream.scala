package playground
import java.nio.file.Paths

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


object FileIOStream extends App {
    implicit val system: ActorSystem = ActorSystem("Demo")
    implicit val materializer: ActorMaterializer = ActorMaterializer()


    val file = Paths.get("greeting.txt")
    val text = Source.single("Hello Akka Stream!")
    val result: Future[IOResult] = text.map(t => ByteString(t)).runWith(FileIO.toPath(file))


//    result.onComplete {
//        case Success(ioResult) =>
//            println(s"Future completed with result: $ioResult")
//            ioResult.status match {
//                case Success(Done) => println("File has been written")
//                case Failure(t) => println(s"File write failure: ${t.getMessage}")
//            }
//        case Failure(t) => println(s"Future Failure: ${t.getMessage}")
//    }

    val mappedResult = result.map {
        case IOResult(size, isDone) =>
            isDone match {
                case Success(Done) => println(s"File write done with file size ${size}")
                case Failure(t) => println(s"File write failure: ${t.getMessage}")
            }
        case _ => println(s"Some other file write failure")
    }

    mappedResult.onComplete {
        case Success(_) => println("All done")
        case Failure(_) => println("All bla!")
    }


}
