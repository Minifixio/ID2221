import org.apache.spark.sql.SparkSession
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

object BuildGraph {

  // Enum to represent the type of graph export
  sealed trait ExportType
  case object WholeGraph extends ExportType
  case object ConnectedComponentsGraph extends ExportType

  def build(hdfsPath: String, exportType: ExportType, minComponentSize: Int = 1, minDegree: Int = 0): (Graph[(String, String, Long), String], Set[VertexId], Map[VertexId, List[String]]) = {
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

    spark.sparkContext.setLogLevel("WARN")

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

    println(s"Number of papers in the dataset: ${papersDF.count()}")

    // Step 1: Collect all valid paper IDs from the dataset
    val validPaperIds: Set[String] = papersDF.select("id").as[String].collect().toSet
    val validVertexIds: Set[VertexId] = validPaperIds.map(_.hashCode.toLong)

    // Step 2: Build vertices (VertexId = hashed paper id, (paper id, title, year))
    val vertices: RDD[(VertexId, (String, String, Long))] = papersDF
      .select("id", "title", "year")
      .rdd
      .map(row => (row.getAs[String]("id").hashCode.toLong, (row.getAs[String]("id"), row.getAs[String]("title"), row.getAs[Long]("year"))))

    // Step 3: Build edges based on references, only if both the source and target paper IDs are valid
    val edges: RDD[Edge[String]] = papersDF
      .select("id", "references")
      .rdd
      .flatMap(row => {
        val paperId = row.getAs[String]("id").hashCode.toLong
        val references = row.getAs[Seq[String]]("references")

        if (references != null) {
          references
            .filter(ref => validPaperIds.contains(ref)) // Only keep references to valid paper IDs
            .map(ref => Edge(paperId, ref.hashCode.toLong, "cites"))
        } else {
          Seq.empty[Edge[String]]
        }
      })

    // Create the initial graph
    val graph: Graph[(String, String, Long), String] = Graph(vertices, edges)

    // Variable to hold the component-papers map (for connected components)
    var componentPapersMap: Map[VertexId, List[String]] = Map()

    // Check the type of graph export
    exportType match {
      case WholeGraph =>
        // For WholeGraph, apply minDegree filter if minDegree > 0
        val filteredGraph = if (minDegree > 0) {
          // Calculate the degrees of vertices and filter based on minDegree
          val filteredVertexIds = graph.degrees
            .filter { case (_, degree) => degree >= minDegree }
            .map(_._1)
            .collect()
            .toSet

          // Create subgraph including only vertices with degree >= minDegree
          graph.subgraph(vpred = (id, _) => filteredVertexIds.contains(id))
        } else {
          graph // No filtering needed if minDegree <= 0
        }

        println(s"Number of vertices in the filtered WholeGraph: ${filteredGraph.numVertices}")
        println(s"Number of edges in the filtered WholeGraph: ${filteredGraph.numEdges}")
        (filteredGraph, validVertexIds, componentPapersMap)

      case ConnectedComponentsGraph =>
        // Find the connected components
        val connectedComponentsGraph = graph.connectedComponents()

        // Group by the component ID to compute the size of each connected component
        val componentSizes = connectedComponentsGraph.vertices
          .map { case (_, componentId) => (componentId, 1) }
          .reduceByKey(_ + _)  // Count the number of vertices in each component

        println("Size of the 10 largest connected components:")
        componentSizes.sortBy(_._2, ascending = false).take(10).foreach(println)

        // Filter components by the minimum size
        val largeComponents = componentSizes.filter { case (_, size) => size >= minComponentSize }
        println(s"Number of connected components with size >= $minComponentSize: ${largeComponents.count()}")

        // Collect the component IDs that satisfy the size condition
        val validComponentIds = largeComponents.map(_._1).collect().toSet

        // Filter vertices by valid component IDs
        val vertexDegrees = graph.degrees.collectAsMap()
        val nonIsolatedComponents = connectedComponentsGraph.vertices
          .filter { case (id, componentId) => vertexDegrees.getOrElse(id, 0) > 0 && validComponentIds.contains(componentId) }

        // Collect valid vertex IDs
        val validVertexIds = nonIsolatedComponents.map(_._1).collect().toSet

        // Now we need to build the new graph of components
        val componentVertices = connectedComponentsGraph.vertices.collectAsMap()

        // Create edges between different components
        val newEdges: RDD[Edge[String]] = edges.filter { edge =>
          val srcComponent = componentVertices(edge.srcId).asInstanceOf[Long]
          val dstComponent = componentVertices(edge.dstId).asInstanceOf[Long]
          srcComponent != dstComponent // Only keep edges between different components
        }.map { edge =>
          val srcComponent = componentVertices(edge.srcId).asInstanceOf[Long]
          val dstComponent = componentVertices(edge.dstId).asInstanceOf[Long]
          Edge(srcComponent, dstComponent, "inter-component")
        }

        println(s"Number of edges in the new graph: ${newEdges.count()}")

        // Create component graph: each node is a component, and each edge is between components
        val componentGraph = Graph.fromEdges(newEdges, defaultValue = (-1L, "", 0L))

        println(s"Number of vertices in the component graph: ${componentGraph.numVertices}")
        println(s"Number of edges in the component graph: ${componentGraph.numEdges}")

        (componentGraph.asInstanceOf[Graph[(String, String, Long), String]], validComponentIds, Map())
    }
  }
}
