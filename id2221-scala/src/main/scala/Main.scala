object Main {

  def hdfsInputPath = "hdfs://localhost:9000/user/emile/dblp_v14-part2-2.json"
  def hdfsOutputPath = "data/graph-export"

  def main(args: Array[String]): Unit = {
    println("Starting to build the graph...")
    var (graph, validVertexIds) = BuildGraph.build(hdfsInputPath, BuildGraph.WholeGraph)
    println("Graph built successfully!")
    ExportGraph.exportGraph(graph, validVertexIds, hdfsOutputPath, ExportGraph.CSVFormat, false)
    println("Graph exported successfully!")
  }
}
