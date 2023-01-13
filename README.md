
LLoyd.java is my serial multidimensional algorithm using points
for however many dimensions we want in our data.

LLoydParallel.java is my parallel multidimensional algirthm
using points for however many dimensions we want in our data, and however many
CORES the current system has to split the data up efficiently and create an 
ideal threadpool.

The Point object can be n-dimensional, so it will still work with 1 dimensional data,
so I removed my initial classes of regular one dimensional data, as it was not as 
interesting and no longer necessary to include once I created the Point object.

The speedups I am seeing are ranging between 3x and 4x. I have timed k=3, dimension=5
and SIZE=10_000_000 and have gotten a 4x speedup, and a 3.6x speedup with 
SIZE=250_000_000.
