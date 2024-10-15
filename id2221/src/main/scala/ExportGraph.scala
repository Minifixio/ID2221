import org.json4s.JsonDSL._
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods._
import org.apache.spark.graphx._
import java.nio.file.{Files, Paths}
import scala.util.Random

object ExportGraph {
  def exportGraph(graph: Graph[(String, String, Long), String], outputPath: String): Unit = {

    // Step 1: Collect valid vertex IDs into a Set
    val validVertexIds: Set[VertexId] = vertices.map(_._1).collect().toSet

    // Step 2: Filter edges to include only those that point to valid vertices
    val filteredEdges = edges.filter { edge =>
        validVertexIds.contains(edge.srcId) && validVertexIds.contains(edge.dstId)
    }

    // Step 3: Convert vertices to Sigma.js format
    val random = new Random()
    val nodesJSON = vertices.map { case (id, (paperId, title, year)) =>
    // Generate random coordinates (you may want to adjust the range as needed)
    val x = random.nextDouble() * 800  // Assuming width of SVG is 800
    val y = random.nextDouble() * 600  // Assuming height of SVG is 600
    JObject(
        "id" -> JInt(id), // Use paperId as the id for the node
        "label" -> JString(title),
        "x" -> JDouble(x),  // Use the generated x coordinate
        "y" -> JDouble(y),  // Use the generated y coordinate
        "size" -> JInt(3)   // Size can be arbitrary; adjust as necessary
    )
    }.collect().toList

    // Step 4: Convert the filtered edges RDD to a JSON array (JArray)
    val edgesJSON = filteredEdges.zipWithIndex.map { case (Edge(srcId, dstId, relationship), index) =>
    JObject(
        "id" -> JString("e" + index), // Create a unique id for each edge
        "source" -> JInt(srcId),
        "target" -> JInt(dstId)
    )
    }.collect().toList

    // Step 5: Combine nodes and edges into a single JSON object for Sigma
    val graphJSON: JObject = JObject(
        "nodes" -> JArray(nodesJSON),
        "edges" -> JArray(edgesJSON)
    )

    // Step 6: Convert to compact JSON string
    val jsonString = compact(render(graphJSON))

    // Step 7: Write the JSON to a file for Sigma.js
    Files.write(Paths.get(outputPath), jsonString.getBytes)
  }
}
