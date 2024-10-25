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

  def exportGraph(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String, format: ExportFormat, isConnectedComponents: Boolean = false, componentPapers: Map[VertexId, List[String]] = Map()): Unit = {
    format match {
      case JSONFormat => exportToJSON(graph, validVertexIds, outputPath, isConnectedComponents)
      case CSVFormat => exportToCSV(graph, validVertexIds, outputPath, isConnectedComponents)
      if (isConnectedComponents && componentPapers.nonEmpty) { 
        exportConnectedComponentGroups(outputPath, componentPapers)
      }
    }
  }

  def exportToJSON(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String, isConnectedComponents: Boolean): Unit = {
    val verticesPath = Paths.get(outputPath + "_vertices.json")
    val vertexWriter = Files.newBufferedWriter(verticesPath)

    try {
      vertexWriter.write("[\n")
      var firstVertex = true

      graph.vertices.mapPartitions { partition =>
        partition.flatMap {
          case (id, (_, _, _)) if isConnectedComponents => 
            // For connected components, we just need the id
            val nodeJson = compact(render(
              JObject(
                "id" -> JInt(id)
              )
            ))
            Some(nodeJson)
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
        if (!firstVertex) vertexWriter.write(",\n")
        vertexWriter.write(jsonString)
        firstVertex = false
      }

      vertexWriter.write("\n]")

    } finally {
      vertexWriter.close()
    }

    // Step 2: Process edges and write them incrementally
    val edgesPath = Paths.get(outputPath + "_edges.json")
    val edgeWriter = Files.newBufferedWriter(edgesPath)

    try {
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
        if (!firstEdge) edgeWriter.write(",\n")
        edgeWriter.write(jsonString)
        firstEdge = false
      }

      edgeWriter.write("\n]")

    } finally {
      edgeWriter.close()
    }

    println(s"Graph successfully exported to $outputPath in JSON format")
  }

  def exportToCSV(graph: Graph[(String, String, Long), String], validVertexIds: Set[VertexId], outputPath: String, isConnectedComponents: Boolean): Unit = {
    def sanitize(text: String): String = {
      text.replaceAll("[^a-zA-Z0-9\\s]", "")
    }

    // Step 1: Collect vertices and create a map from vertex ID to sanitized paper title
    val vertexIdToTitle: Map[VertexId, String] = if (!isConnectedComponents) {
      graph.vertices
        .filter {
          case (id, attr) => attr match {
            case (paperId: String, title: String, year: Long) => paperId != null && title != null
            case _ => false
          }
        }
        .map {
          case (id, (paperId: String, title: String, year: Long)) => (id, sanitize(title))
        }
        .collect()
        .toMap
    } else {
      Map.empty[VertexId, String] // No titles needed for connected components
    }

    // Step 2: Create edges CSV
    var edgesPath: Path = null
    if (isConnectedComponents) {
      edgesPath = Paths.get(outputPath + "_cc_edges.csv")
    } else {
      edgesPath = Paths.get(outputPath + "_cc_metadata.csv")
    }
    val edgeWriter = Files.newBufferedWriter(edgesPath)

    try {
      edgeWriter.write("source;target\n")

      graph.edges.mapPartitions { partition =>
        partition.flatMap {
          case Edge(srcId, dstId, relationship) if validVertexIds.contains(srcId) && validVertexIds.contains(dstId) =>
            if (isConnectedComponents) {
              // For connected components, just output ids
              Some(s"$srcId;$dstId")
            } else {
              // Use sanitized titles for normal graphs
              // Safely retrieve titles with an option
              val srcTitleOpt = vertexIdToTitle.get(srcId)
              val dstTitleOpt = vertexIdToTitle.get(dstId)
              
              // Use `for-comprehension` to construct the CSV line only if both titles are present
              for {
                srcTitle <- srcTitleOpt
                dstTitle <- dstTitleOpt
              } yield s"$srcTitle;$dstTitle"
            }
          case _ => None
        }
      }.collect().foreach { csvLine =>
        edgeWriter.write(csvLine + "\n")
      }

    } finally {
      edgeWriter.close()
    }


    // Step 3: Create metadata CSV for vertices
    var metadataPath: Path = null
    if (isConnectedComponents) {
      metadataPath = Paths.get(outputPath + "_cc_metadata.csv")
    } else {
      metadataPath = Paths.get(outputPath + "_metadata.csv")
    }
    
    val metadataWriter = Files.newBufferedWriter(metadataPath)

    try {
      metadataWriter.write("id;color;size\n")

      // This metadata creation logic is the same for both types of graphs
      val color = "red"
      val size = 10

      graph.vertices.mapPartitions { partition =>
        partition.flatMap {
          case (id, (paperId, title, year)) if !isConnectedComponents && paperId != null && title != null =>
            Some(s"${sanitize(title)};$color;$size")
          case (id, _) if isConnectedComponents =>
            Some(s"$id;$color;$size") // Just output id for connected components
          case _ => None
        }
      }.collect().foreach { csvLine =>
        metadataWriter.write(csvLine + "\n")
      }

    } finally {
      metadataWriter.close()
    }
  }

  def exportConnectedComponentGroups(outputPath: String, componentPapers: Map[VertexId, List[String]] = Map()) {
    // Export the list of papers in each connected component if available
    val componentsPath = Paths.get(outputPath + "_components.json")
    val componentsWriter = Files.newBufferedWriter(componentsPath)

    try {
      componentsWriter.write("[\n")
      var firstComponent = true

      componentPapers.foreach { case (componentId, papers) =>
        if (!firstComponent) componentsWriter.write(",\n")

        val componentJson = compact(render(
          JObject(
            "componentId" -> JInt(componentId),
            "papers" -> JArray(papers.map(paperId => JString(paperId)).toList)
          )
        ))
        componentsWriter.write(componentJson)
        firstComponent = false
      } 

      componentsWriter.write("\n]")
    } finally {
      componentsWriter.close()
    }
  }
}

