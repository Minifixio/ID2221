object Main {

  def hdfsInputPath = "hdfs://localhost:9000/user/emile/dblp_v14-part2-1.json"
  def hdfsOutputPath = "data/graph-export"

  def main(args: Array[String]): Unit = {
    println("Starting to build the graph...")
    var (graph, validVertexIds) = BuildGraph.build(hdfsInputPath)
    println("Graph built successfully!")
    ExportGraph.exportGraph(graph, validVertexIds, hdfsOutputPath, ExportGraph.CSVFormat)
    println("Graph exported successfully!")
  }
}
