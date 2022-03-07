* A number of servers are assigned to pregenerate worlds. They may also be asked to do certain kinds of generation for certain challenges, over websockets
* Players wait for their world to become updated in a small room in the default world
* NO world generation happens in the challenge servers, none at all
* World gen servers are assigned a queue of needed worlds to generate. They are asked by websocket and they respond by websocket, which is the Controller's queue to copy that directory to the accumulating list of servers that request that item
* Challenges can ask for a type of world and all requests of the same type will be lumped together and satisfied at the same time
* Plugins designed for world gen in order to handle custom world gen (IE 90% of chunks removed)