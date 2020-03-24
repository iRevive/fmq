---
id: background-consumer
title: Background consumer
---

The `socket.receive` method blocks the thread until a new message is available (based on a configuration).  
To avoid additional thread-shifts, the consuming process can be evaluated on a blocking thread as a background process.  

## Example

TBD