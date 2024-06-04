[log filter syntax]

level:D                  // debug only
level:D*                 // debug and higher (info, warn, error)
app:com.test.pm          // app name equals
tag:HD_*                 // tag start with "HD_"
tag:*HD_                 // tag ends with "HD_"
tag:!HD_*                // tag does NOT end with "HD_"
msg:"hello world"        // message contains "hello world"
*:*hello*                // any field contains "hello"

[combining multiple filteres]

FILTER1 && FILTER2 && FILTER3

