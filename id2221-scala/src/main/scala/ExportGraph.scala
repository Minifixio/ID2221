import org.json4s.JsonDSL._
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods._
import org.apache.spark.graphx._
import java.nio.file.{Files, Paths, Path}
import java.nio.file.StandardOpenOption
import org.apache.spark.rdd.RDD

object ExportGraph {

  sealed trait ExportFormat
  case object JSONFormat extends ExportFormat
  case object CSVFormat extends ExportFormat

  def exportGraph(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String, format: ExportFormat): Unit = {
    format match {
      case JSONFormat => exportToJSON(graph, validVertexIds, outputPath)
      case CSVFormat => exportToCSV(graph, validVertexIds, outputPath)
    }
  }

  // JSON export (same as the previous JSON export but extracted to its own function)
  def exportToJSON(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String): Unit = {
    val verticesPath = Paths.get(outputPath + "_vertices.json")
    val vertexWriter = Files.newBufferedWriter(verticesPath)

    try {
      // Write the opening JSON array for vertices
      vertexWriter.write("[\n")
      var firstVertex = true

      graph.vertices.mapPartitions { partition =>
        partition.flatMap {
          case (id, (paperId, title, year)) if paperId != null && title != null =>
            val nodeJson = compact(render(
              JObject(
                "id" -> JInt(id),
                "label" -> JString(title)
              )
            ))

            Some(nodeJson)
          case _ => None
        }
      }.collect().foreach { jsonString =>
        if (!firstVertex) vertexWriter.write(",\n")  // Add a comma between nodes
        vertexWriter.write(jsonString)
        firstVertex = false
      }

      // Write the closing bracket for the vertices JSON array
      vertexWriter.write("\n]")

    } finally {
      vertexWriter.close() // Ensure the file writer is closed
    }

    // Step 2: Process edges and write them incrementally
    val edgesPath = Paths.get(outputPath + "_edges.json")
    val edgeWriter = Files.newBufferedWriter(edgesPath)

    try {
      // Write the opening JSON array for edges
      edgeWriter.write("[\n")
      var firstEdge = true

      graph.edges.mapPartitions { partition =>
        partition.flatMap {
          case Edge(srcId, dstId, relationship) if validVertexIds.contains(srcId) && validVertexIds.contains(dstId) =>
            val edgeJson = compact(render(
              JObject(
                "source" -> JInt(srcId),
                "target" -> JInt(dstId)
              )
            ))
            Some(edgeJson)
          case _ => None
        }
      }.collect().foreach { jsonString =>
        if (!firstEdge) edgeWriter.write(",\n")  // Add a comma between edges
        edgeWriter.write(jsonString)
        firstEdge = false
      }

      // Write the closing bracket for the edges JSON array
      edgeWriter.write("\n]")

    } finally {
      edgeWriter.close() // Ensure the file writer is closed
    }

    println(s"Graph successfully exported to $outputPath in JSON format")
  }

    // CSV export with sanitized titles
  def exportToCSV(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String): Unit = {
    // Helper function to sanitize text by keeping only letters, numbers, and spaces
    def sanitize(text: String): String = {
      text.replaceAll("[^a-zA-Z0-9\\s]", "")  // Remove all characters except letters, numbers, and spaces
    }

    // Step 1: Collect vertices and create a map from vertex ID to sanitized paper title
    val vertexIdToTitle: Map[VertexId, String] = graph.vertices
      .filter {
        case (id, attr) => attr match {
          case (paperId: String, title: String, year: Long) => paperId != null && title != null
          case _ => false // Handle cases where the attribute is null or not a tuple with 3 elements
        }
      }
      .map {
        case (id, (paperId: String, title: String, year: Long)) => (id, sanitize(title))
      }
      .collect()
      .toMap

    // Step 2: Create edges CSV
    val edgesPath = Paths.get(outputPath + "_edges.csv")
    val edgeWriter = Files.newBufferedWriter(edgesPath)

    try {
      // Write header for edges
      edgeWriter.write("source;target\n")

      // Process edges and write each to CSV using the sanitized titles instead of IDs
      graph.edges.mapPartitions { partition =>
        partition.flatMap {
          case Edge(srcId, dstId, relationship) if validVertexIds.contains(srcId) && validVertexIds.contains(dstId) =>
            // Look up the sanitized titles for the source and target IDs
            for {
              srcTitle <- vertexIdToTitle.get(srcId)
              dstTitle <- vertexIdToTitle.get(dstId)
            } yield s"$srcTitle;$dstTitle"
          case _ => None
        }
      }.collect().foreach { csvLine =>
        edgeWriter.write(csvLine + "\n")
      }

    } finally {
      edgeWriter.close() // Ensure the file writer is closed
    }

    // Step 3: Create metadata CSV for vertices
    val metadataPath = Paths.get(outputPath + "_metadata.csv")
    val metadataWriter = Files.newBufferedWriter(metadataPath)

    try {
      // Write header for metadata
      metadataWriter.write("id;color;size\n")

      // Process vertices and write sanitized metadata for each vertex to CSV
      val color = "red" // Same color for all nodes, you can change this logic
      val size = 10     // Same size for all nodes, you can change this logic

      graph.vertices.mapPartitions { partition =>
        partition.flatMap {
          case (id, (paperId, title, year)) if paperId != null && title != null =>
            Some(s"${sanitize(title)};$color;$size")
          case _ => None
        }
      }.collect().foreach { csvLine =>
        metadataWriter.write(csvLine + "\n")
      }

    } finally {
      metadataWriter.close() // Ensure the file writer is closed
    }

    println(s"Graph successfully exported to $outputPath in CSV format")
  }
}