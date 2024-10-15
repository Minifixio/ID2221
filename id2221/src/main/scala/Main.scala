@main def hello(): Unit =
  println("Starting to build the graph...")
  graph = BuildGraph.build(os.read(os.pwd / "data" / "dblp_v14-part2266-2.json"))
  println("Graph built successfully!")
  ExportGraph.exportGraph(graph, os.read(os.pwd / "data" / "graph-export.json"))
  println(msg)

def msg = "I was compiled by Scala 3. :)"
