import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

object BuildGraph {
  def build(jsonFilePath: String): Unit = {
    val spark = SparkSession.builder()
      .appName("AcademicGraphAnalysis")
      .config("spark.sql.caseSensitive", "true")
      .getOrCreate()

    import spark.implicits._

    val papersDF = spark.read.option("multiline", "true")
      .json(jsonFilePath)
      .select(
        "id",
        "title",
        "doi",
        "keywords",
        "n_citation",
        "year",
        "issn",
        "url",
        "abstract",
        "authors",
        "doc_type",
        "v12_authors",
        "references",
        "v12_id"
      )

    val vertices: RDD[(VertexId, (String, String, Long))] = papersDF
        .select("id", "title", "year")
        .rdd
        .map(row => (row.getAs[String]("id").hashCode.toLong, (row.getAs[String]("id"), row.getAs[String]("title"), row.getAs[Long]("year"))))

    val edges: RDD[Edge[String]] = papersDF
    .select("id", "references")
    .rdd
    .flatMap(row => {
        val paperId = row.getAs[String]("id").hashCode.toLong
        val references = row.getAs[Seq[String]]("references")
        
        // Check if references is not null before mapping
        if (references != null) {
            references.map(ref => Edge(paperId, ref.hashCode.toLong, "cites"))
        } else {
            // If references is null, return an empty list
            Seq.empty[Edge[String]]
        }
    })

    val graph: Graph[(String, String, Long), String] = Graph(vertices, edges)

    return graph
  }
}