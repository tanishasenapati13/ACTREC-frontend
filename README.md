# ACTREC Frontend

A visual block-based programming interface that enables users to create, connect, and execute computational workflows through an intuitive drag-and-drop system.

---

## Prerequisites

- **Java** (JDK 8 or higher) - Required
- **VS Code** (Optional) - Recommended for development

---

## Installation

```bash
git clone https://github.com/tanishasenapati13/ACTREC-frontend.git
cd ACTREC-frontend
javac gui4.java
java gui4.java
```

---

## Interface Components

### 1. **Drawing Panel**
The main workspace where blocks are displayed and manipulated. Blocks can be added by:
- Clicking directly on block names in the panel
- Selecting from the dropdown menu and clicking "Add Block"

### 2. **Block Library Dropdown**
A searchable dropdown containing all available block types that can be instantiated and added to the drawing panel.

### 3. **Custom Block Creation**
Create new block types with customizable parameters:
- **Name**: Define a unique identifier for your block
- **Input Configuration**: Specify the number and type of inputs
- **Output Configuration**: Specify the number and type of outputs
- **Type Selection**: Choose appropriate data types for each input/output connection

### 4. **Search Functionality**
Quickly locate blocks on the canvas:
- Enter a block name in the search bar
- Matching blocks are highlighted automatically
- Useful for navigating complex workflows

### 5. **Canvas Navigation**
- **Zoom In/Out**: Use dedicated buttons or two-finger pinch gesture on trackpad
- **Pan**: Drag the canvas to reposition your view

### 6. **Execution Engine**
Processes the workflow and generates an `execution.txt` file containing:
- Execution order (topologically sorted)
- Block configurations
- Connection mappings
- Input values

### 7. **Hamburger Menu Options**

#### a) Edit Input Values
Modify default values for unconnected inputs:
- Only available for inputs without incoming connections
- Values must match the specified input type
- Provides default behavior when no data source is connected

#### b) Naming History
Track block identification changes over time:
- **Current Name**: Active block identifier
- **Original Name**: Initial block name at creation
- **History Log**: Complete timeline of all naming modifications

---

## Workflow Guide

### Getting Started

1. **Add Blocks to Canvas**
   - Click a block name from the drawing panel, OR
   - Select from the dropdown menu and click "Add Block"

2. **Position Blocks**
   - Click and drag blocks to organize your workflow
   - Arrange blocks logically to represent data flow

3. **Inspect Connection Types**
   - **Output Types**: Hover over the right-side arrow (→) to see output data type
   - **Input Types**: Hover over the left-side arrow to see required input data type

4. **Connect Blocks**
   - Click on an output arrow (→)
   - Click on a compatible input arrow to establish connection
   - Visual feedback confirms successful connection

5. **Execute Workflow**
   - Click the "Execute" button
   - System generates `execution.txt` with results
   - Check for error messages if execution fails

---

## Advanced Features

### Topological Sorting
- Automatically determines optimal execution order
- Blocks execute in dependency-aware sequence
- Results written to `execution.txt` in execution order

### Cycle Detection
- Validates workflow for circular dependencies
- Prevents infinite loops during execution
- Displays error message if cycles are detected

### Type Safety
- Enforces type compatibility between connections
- Output type must match input type for valid connections
- Displays error popup when type mismatch is detected

### Default Value Management
- Set fallback values for unconnected inputs
- Only editable through "Edit Input Values" menu
- Must conform to specified input type

### Block Context Menu (Right-Click)
Access block-specific operations:
1. **Rename**: Change the block's display name
2. **Modify Inputs**: Add/remove inputs and update their types
3. **Delete Block**: Remove block and all its connections

### Connection Context Menu (Right-Click)
Manage individual connections:
- **Delete Connection**: Remove the link between blocks

---

## Execution File Structure

The `execution.txt` file contains:

1. **Execution Order**
   - Blocks listed in topologically sorted sequence
   - Ensures dependencies are resolved before execution

2. **Block Configurations**
   - All instantiated block names
   - Input specifications for each block
   - Default values OR connected output references (format: `block_name.output_number`)

3. **Connection Map**
   - Complete list of output-to-input connections
   - Special handling for status connections between blocks

---

## Best Practices

- **Type Checking**: Always verify input/output type compatibility before connecting
- **Naming Conventions**: Use descriptive block names for complex workflows
- **Default Values**: Provide sensible defaults for optional inputs
- **Workflow Testing**: Execute frequently to catch connection errors early
- **Organization**: Arrange blocks logically to improve workflow readability

---

## Error Handling

**Cycle Detected** - Circular block dependencies exist. Review connections and break the loop.

**Type Mismatch** - Incompatible input/output types. Connect only matching types.

**Invalid Default Value** - Wrong data type for input. Enter value matching specified type.

**Execution Failed** - Missing required inputs. Ensure all inputs have values or connections.
