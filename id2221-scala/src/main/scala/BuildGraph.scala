import org.apache.spark.sql.SparkSession
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

object BuildGraph {
  
  // Enum to represent the type of graph export
  sealed trait ExportType
  case object WholeGraph extends ExportType
  case object ConnectedComponentsGraph extends ExportType

  def build(hdfsPath: String, exportType: ExportType): (Graph[(String, String, Long), String], Set[VertexId]) = {
    val spark = SparkSession.builder()
      .appName("AcademicGraphAnalysis")
      .config("spark.sql.caseSensitive", "true")
      .config("spark.master", "local")
      .config("spark.executor.memory", "16g")
      .config("spark.driver.memory", "16g")
      .config("spark.executor.extraJavaOptions", "-XX:+UseG1GC")
      .config("spark.driver.extraJavaOptions", "-XX:+UseG1GC")
      .config("spark.memory.fraction", "0.8")
      .config("spark.memory.storageFraction", "0.4")
      .getOrCreate()

    import spark.implicits._

    println("Reading JSON file located at: " + hdfsPath)
    val papersDF = spark.read.option("multiline", "true")
      .json(hdfsPath)
      .select(
        "id",
        "title",
        "year",
        "references"
      )

    // Build vertices (VertexId = hashed paper id, (paper id, title, year))
    val vertices: RDD[(VertexId, (String, String, Long))] = papersDF
      .select("id", "title", "year")
      .rdd
      .map(row => (row.getAs[String]("id").hashCode.toLong, (row.getAs[String]("id"), row.getAs[String]("title"), row.getAs[Long]("year"))))

    // Build edges based on references
    val edges: RDD[Edge[String]] = papersDF
      .select("id", "references")
      .rdd
      .flatMap(row => {
        val paperId = row.getAs[String]("id").hashCode.toLong
        val references = row.getAs[Seq[String]]("references")

        if (references != null) {
          references.map(ref => Edge(paperId, ref.hashCode.toLong, "cites"))
        } else {
          Seq.empty[Edge[String]]
        }
      })

    // Create the initial graph
    val graph: Graph[(String, String, Long), String] = Graph(vertices, edges)

    // Check the type of graph export
    exportType match {
      case WholeGraph => 
        // Return the whole graph as-is
        val validVertexIds = vertices.map(_._1).collect().toSet
        (graph, validVertexIds)

      case ConnectedComponentsGraph =>
        // Find the connected components
        val connectedComponentsGraph = graph.connectedComponents()

        // Remove isolated nodes (nodes that are their own connected component and have no edges)
        // First, collect the degrees and then join them
        val vertexDegrees = graph.degrees.collectAsMap() // Collecting as a map for efficient access
        val nonIsolatedComponents = connectedComponentsGraph.vertices
          .filter { case (id, _) => vertexDegrees.getOrElse(id, 0) > 0 } // Exclude nodes with degree 0

        // Collect valid vertex IDs
        val validVertexIds = nonIsolatedComponents.map(_._1).collect().toSet

        // Now create the non-isolated graph using IDs that are valid
        val nonIsolatedGraph = graph.subgraph(vpred = (id, _) => validVertexIds.contains(id))

        (nonIsolatedGraph, validVertexIds)
    }
  }
}
