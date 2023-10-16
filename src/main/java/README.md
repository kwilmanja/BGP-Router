Project 3: Joseph Kwilman + Andrew Panzone

Run: ./3700router <asn> <port-ip.add.re.ss-[peer,prov,cust]> [port-ip.add.re.ss-[peer,prov,cust]] ...[port-ip.add.re.ss-[peer,prov,cust]]

Overview: The BGP Router simulates the construction and maintenance of a BGP router. The router
receives update/withdraw messages form its neighbors to build its routing table. It then uses 
BGP to decide where to forward data to. The router implements aggregation of networks in its routing table.

Design: The main function is responsible for parsing the command line arguments and 
initializing/running our router.
NetUtil is a utility class for receiving/sending JSON messages over a DatagramChannel
The Router class represents our local BGP router. It is created with its asn number and its connected networks.
It sends a handshake out to each network connected when created. When run, the router
wait for incoming messages (update, withdraw, data, dump) then handles them accordingly.
The Router has an instance of a RoutingTable. The RoutingTable class is responsible for 
maintaining a collection of routes and answering a query about which neighboring router to send a data packet to.
The Route class holds the available information about a route. Finally, the IPAddress
class has methods to help convert ip strings to binary and netmask strings to ints.


Testing: to test the code, run it on the test simulator.

Challenges:
The hardest part of the assignment was organizing the data in the routing table and figuring out how to add/withdraw
routes in an efficient manner (namely disaggregation). Our code rebuilds the table from the stored messages
after every withdraw.