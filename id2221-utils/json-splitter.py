import json

def fix_cut_json(file_path):
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()
    
    last_complete_index = content.rfind('}')  # For JSON object
    if last_complete_index == -1:  # If not found, look for array
        last_complete_index = content.rfind(']')

    if last_complete_index != -1:
        # Create a valid JSON up to the last complete element
        valid_json = content[:last_complete_index + 1]  # Include the bracket

        # Try to load the valid part
        try:
            data = json.loads(valid_json)
            print("Fixed JSON:")
            # print(json.dumps(data, indent=4))
            
            # Save the fixed JSON to a new file
            with open('fixed_output.json', 'w', encoding='utf-8') as output_file:
                json.dump(data, output_file, ensure_ascii=False, indent=4)
            print("Fixed JSON saved to 'fixed_output.json'")
        except json.JSONDecodeError as e:
            print(f"Still invalid JSON after fix: {e}")
    else:
        print("Could not find any valid closing brackets to fix the JSON.")
        

if __name__ == "__main__":
    file_path = '../id2221-scala/data/dblp_v14.json'  # Replace with your file path
    fix_cut_json(file_path)

