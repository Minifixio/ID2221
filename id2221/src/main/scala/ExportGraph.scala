import org.json4s.JsonDSL._
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods._
import org.apache.spark.graphx._
import java.nio.file.{Files, Paths, Path}
import scala.util.Random
import org.apache.spark.rdd.RDD
import java.nio.file.StandardOpenOption

object ExportGraph {
  def exportGraph(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String): Unit = {

    // Step 1: Collect vertices and edges from RDDs into local collections (Seq)
    val vertices: Seq[(VertexId, (String, String, Long))] = graph.vertices.collect().toSeq
    val edges: Seq[Edge[String]] = graph.edges.collect().toSeq

    // Step 2: Collect valid vertex IDs into a Set
    println(s"Number of vertices: ${vertices.length}")
    println(s"Number of edges: ${edges.length}")
    println(s"Valid vertex IDs: ${validVertexIds}")

    // Step 3: Filter edges to include only those that point to valid vertices
    val filteredEdges = edges.filter { edge =>
      validVertexIds.contains(edge.srcId) && validVertexIds.contains(edge.dstId)
    }

    // Step 4: Convert vertices to Sigma.js format, filtering out null attributes
    val random = new Random()
    val nodesJSON: List[JValue] = vertices.collect {
      case (id, (paperId, title, year)) if paperId != null && title != null =>
        // Generate random coordinates (adjust range if needed)
        val x = random.nextDouble() * 800
        val y = random.nextDouble() * 600
        JObject(
          "id" -> JInt(id),         // Vertex ID
          "label" -> JString(title), // Paper title
          "x" -> JDouble(x),         // Random x coordinate
          "y" -> JDouble(y),         // Random y coordinate
          "size" -> JInt(3)          // Default size
        ): JValue // Ensure the returned value is of type JValue
    }.toList

    // Step 5: Convert filtered edges to Sigma.js format
    val edgesJSON: List[JValue] = edges.collect {
      case Edge(srcId, dstId, relationship) if validVertexIds.contains(srcId) && validVertexIds.contains(dstId) =>
        // Generate random coordinates (adjust range if needed)
        JObject(
          "source" -> JInt(srcId),
          "target" -> JInt(dstId)
        ): JValue
    }.toList

    // Step 6: Combine nodes and edges into a JSON object for Sigma.js
    val graphJSON: JObject = JObject(
      "nodes" -> JArray(nodesJSON), // Make sure it's List[JValue]
      "edges" -> JArray(edgesJSON)  // Make sure it's List[JValue]
    )

    // Step 7: Convert to compact JSON string
    val jsonString = compact(render(graphJSON))

    // Step 8: Save the JSON to a file
    val outputDir: Path = Paths.get(outputPath).getParent

    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir) // Create the directory and any missing parent directories
      println(s"Directory created: $outputDir")
    } else {
      println(s"Directory already exists: $outputDir")
    }

    try {
      Files.write(Paths.get(outputPath), jsonString.getBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
      println(s"Successfully wrote to: $outputPath")
    } catch {
      case e: Exception =>
        println(s"Error writing to file: ${e.getMessage}")
    }
  }
}
