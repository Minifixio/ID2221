object Main {

  def hdfsInputPath = "hdfs://localhost:9000/data/dblp_v14-part2266-3.json"
  def hdfsOutputPath = "data/graph-export.json"

  def main(args: Array[String]): Unit = {
    println("Starting to build the graph...")
    var (graph, validVertexIds) = BuildGraph.build(hdfsInputPath)
    println("Graph built successfully!")
    ExportGraph.exportGraph(graph, validVertexIds, hdfsOutputPath)
    println("Graph exported successfully!")
  }
}
