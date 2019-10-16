# Spring Cloud Gateway RSocket Sample

## TODO:

- [ ] Move ping and pong to use Spring Framework RSocket Messaging rather than raw RSocket
- [ ] Multiple pong servers to highlight gateway load balancing.
- [ ] Golang ping requester to highlight RSocket polyglot
- [ ] JS ping requester from browser. (Likely needs changes in gateway)

## Succesful log messages

When ping and pong are communicating correctly, you should see logs like the following in pong:
```
2019-08-09 11:32:19.477  INFO 16726 --- [tor-tcp-epoll-1] o.s.c.r.s.pong.PongApplication$Pong      : received ping1(1) in Pong
2019-08-09 11:32:20.186  INFO 16726 --- [tor-tcp-epoll-1] o.s.c.r.s.pong.PongApplication$Pong      : received ping1(2) in Pong
```

And in ping you should see logs like:
```
2019-08-09 11:32:19.480  INFO 16077 --- [tor-tcp-epoll-1] o.s.c.r.s.ping.PingApplication$Ping      : received pong(1) in Ping1
2019-08-09 11:32:20.189  INFO 16077 --- [tor-tcp-epoll-1] o.s.c.r.s.ping.PingApplication$Ping      : received pong(2) in Ping1
```

## Direct Mode: No Gateway

Run pong with `spring.profiles.active=server`. 
Then run ping with `spring.profiles.active=gateway-instance-1`

## Single Gateway Mode

Run gateway first. 
Then run ping with `spring.profiles.active=ping-instance-1,gateway-instance-1`.

You should see backpressure logs like:
```
2019-08-09 11:30:59.812  INFO 15199 --- [     parallel-2] o.s.c.r.s.ping.PingApplication$Ping      : Dropped payload ping1
2019-08-09 11:31:00.811  INFO 15199 --- [     parallel-2] o.s.c.r.s.ping.PingApplication$Ping      : Dropped payload ping1
```

Run pong with `spring.profiles.active=pong-instance-1,gateway-instance-1`.

You should see:

```
2019-08-09 11:32:19.480  INFO 16077 --- [tor-tcp-epoll-1] o.s.c.r.s.ping.PingApplication$Ping      : received pong(1) in Ping1
2019-08-09 11:32:20.189  INFO 16077 --- [tor-tcp-epoll-1] o.s.c.r.s.ping.PingApplication$Ping      : received pong(2) in Ping1
```

... i.e. the connection between Ping and Pong has been established via the Gateway.

## Gateway Cluster Mode

Run a Gateway instance.
Run a second Gateway instance with `spring.profiles.active=gateway-instance-2`.

You should see logs like this in 2nd gateway node:
```
2019-08-09 11:36:12.524 DEBUG 19644 --- [tor-tcp-epoll-1] o.s.c.gateway.rsocket.registry.Registry  : Registering RSocket: [Metadata@5015196c name = 'ping', properties = map['id' -> 'pingproxy1']]
2019-08-09 11:36:12.526 DEBUG 19644 --- [tor-tcp-epoll-1] o.s.c.g.rsocket.registry.RegistryRoutes  : Created Route for registered service [Route@57801e07 id = 'ping', targetMetadata = [Metadata@5015196c name = 'ping', properties = map['id' -> 'pingproxy1']], order = 0, predicate = org.springframework.cloud.gateway.rsocket.registry.RegistryRoutes$$Lambda$536/302508515@57d6f132, gatewayFilters = list[[empty]]]
```

And in the first gateway node:
```
2019-08-09 11:36:12.573 DEBUG 19475 --- [or-http-epoll-2] o.s.c.gateway.rsocket.registry.Registry  : Registering RSocket: [Metadata@318e483 name = 'pong', properties = map['id' -> 'gateway21']]
2019-08-09 11:36:12.575 DEBUG 19475 --- [or-http-epoll-2] o.s.c.g.rsocket.registry.RegistryRoutes  : Created Route for registered service [Route@6796b8e4 id = 'pong', targetMetadata = [Metadata@318e483 name = 'pong', properties = map['id' -> 'gateway21']], order = 0, predicate = org.springframework.cloud.gateway.rsocket.registry.RegistryRoutes$$Lambda$523/976465559@11bf03ce, gatewayFilters = list[[empty]]]
2019-08-09 11:36:12.576 DEBUG 19475 --- [or-http-epoll-2] o.s.c.g.r.s.SocketAcceptorFilterChain    : filter chain completed with success
```

Start Ping with `spring.profiles.active=ping-instance-1,gateway-instance-1`
You should see backpressure logs as above.

Start Pong with `spring.profiles.active=pong-instance-1,gateway-instance-2`
You should see successful log messages in ping and pong.

## Additional ping client

During any mode, you can run another ping client with `spring.profiles.active=ping-instance-2,gateway-instance-1`. 
By default ping client (`ping-instance-1`) uses the 'request channel' RSocket method, where ping 2 (`ping-instance-2`) uses 'request reply'.

The logs in pong will now show additional client pings such as:
```
2019-08-09 11:43:58.309  INFO 22645 --- [tor-tcp-epoll-1] o.s.c.r.s.pong.PongApplication$Pong      : received ping1(280) in Pong
2019-08-09 11:43:58.449  INFO 22645 --- [tor-tcp-epoll-1] o.s.c.r.s.pong.PongApplication$Pong      : received ping2(281) in Pong
```

## Profile Specific Configuration

To see what each profile is setting, see the `application.yml` for each individual project. Each profile is another yaml document.

## Advanced Tests

### Failure Resiliency 1

Start two Gateway instances:
1. Run Gateway instance 1
1. Run Gateway instance 2 with `spring.profiles.active=gateway-instance-2`. 
1. Run ping instance 1 with `spring.profiles.active=ping-instance-1,gateway-instance-1`

You should see the backpressure messages from above.
Now start two pong instances:

1. Run pong instance 1 with `spring.profiles.active=pong-instance-1,gateway-instance-2`
1. Run pong instance 2 with `spring.profiles.active=pong-instance-2,gateway-instance-2`

You should now see pong messages arriving in ping's console output. Likewise, one of the two pong instances will print that it received pings.

Now, stop one pong instance.

In the output of ping you should see the following:

```
2019-10-16 15:00:28.749 ERROR 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : Received an error from server.

io.rsocket.exceptions.ApplicationErrorException: 
	at io.rsocket.exceptions.Exceptions.from(Exceptions.java:45) ~[rsocket-core-1.0.0-RC5.jar:na]
	at io.rsocket.RSocketRequester.handleFrame(RSocketRequester.java:556) ~[rsocket-core-1.0.0-RC5.jar:na]
...

2019-10-16 15:00:28.750 ERROR 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : Retrying
```

After that you should see ping retry immediately, and see the pongs continuing - this time from the second, remaining pong instance.

### Failure Resiliency 2

Start two Gateway instances:
1. Run Gateway instance 1
1. Run Gateway instance 2 with `spring.profiles.active=gateway-instance-2`. 
1. Run ping instance 1 with `spring.profiles.active=ping-instance-1,gateway-instance-1`

You should see the backpressure messages from above.
Now start a single pong instance:

1. Run pong instance 1 with `spring.profiles.active=pong-instance-1,gateway-instance-2`

You should now see pong messages arriving in ping's console output. Likewise, one of the two pong instances will print that it received pings.

Now, stop the pong instance.

In the output of ping you should see the following:

```
2019-10-16 15:00:28.749 ERROR 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : Received an error from server.

io.rsocket.exceptions.ApplicationErrorException: 
	at io.rsocket.exceptions.Exceptions.from(Exceptions.java:45) ~[rsocket-core-1.0.0-RC5.jar:na]
	at io.rsocket.RSocketRequester.handleFrame(RSocketRequester.java:556) ~[rsocket-core-1.0.0-RC5.jar:na]
...

2019-10-16 15:00:28.750 ERROR 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : Retrying
```

Now start the pong instance again with `spring.profiles.active=pong-instance-1,gateway-instance-2`.

Ping should continue to receive pongs now:

```
2019-10-16 15:04:40.477  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(765) in Ping for route ID 1
2019-10-16 15:04:40.486  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(766) in Ping for route ID 1
2019-10-16 15:04:40.486  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(767) in Ping for route ID 1
2019-10-16 15:04:40.486  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(768) in Ping for route ID 1
2019-10-16 15:04:40.486  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(769) in Ping for route ID 1
2019-10-16 15:04:40.486  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(770) in Ping for route ID 1
2019-10-16 15:04:40.487  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(771) in Ping for route ID 1
2019-10-16 15:04:40.487  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(772) in Ping for route ID 1
2019-10-16 15:04:40.487  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(773) in Ping for route ID 1
2019-10-16 15:04:40.487  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(774) in Ping for route ID 1
2019-10-16 15:04:40.487  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(775) in Ping for route ID 1
2019-10-16 15:04:40.487  INFO 98077 --- [actor-tcp-nio-1] o.s.c.rsocket.sample.ping.PingService    : received pong(776) in Ping for route ID 1
```

❗️**Note**: As soon as pong is back up, ping will receive all the previous pong's it was requesting while pong was down. This means that currently, the Gateway buffers ping's requests and forwards them to pong, once it is back.
This is actually not the intended behavior of Gateway. Instead, Gateway should apply maximum backpressure to ping, forcing ping to stop sending pings or dropping them.
Gateway already does that when ping is started before pong is available. For some reason it does not, when pong goes away while ping is still there. We consider this a bug in Gateway.

