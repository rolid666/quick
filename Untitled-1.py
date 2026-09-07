test = [12, 58, 44,1,545, 87, 98, 41,57,86,96]
sort_data = test

def bouble_sort(arr):
    n = len(arr)
    for i in range(n-1):
        swapping = False
        for j in range(n-1-i):
            if arr[j] > arr[j+1]:
                arr[j],arr[j+1] = arr[j+1],arr[j]
                swapping = True
        if not swapping:
            break
    return arr
print (sort_data) 
RESULT = bouble_sort(test)

print (RESULT)
