# jdk11 源码
##  java.base



### java.io：



### java.lang：

#### Object 



#### Integer

1. **Integer** **的声明**

2. **主要属性**

3. 构造方法

4. **toString() toString(int i) toString(int i, int radix)**

5. **自动拆箱和装箱**

   - Integet.valueOf()

   - intValue()

6. **equals(Object obj)****

7. **hashCode()**

8. **parseInt(String s)** **和** **parseInt(String s, int radix)** **

9. **compareTo(Integer anotherInteger) 和 compare(int x, int y)**



#### String

1.  **String 类的定义**
2.  **字段属性**
3.  **构造⽅法**
4.  **equals(Object anObject)** 
5.  **hashCode()**
6.  **charAt(int index)**
7.  **compareTo(String anotherString) 和 compareToIgnoreCase(String str)** 
8.  **concat(String str)** 
9.  **indexOf(int ch) 和 indexOf(int ch, int fromIndex)**
10.  **split(String regex) 和 split(String regex, int limit)**
11.  **replace(char oldChar, char newChar) 和 String replaceAll(String regex, String replacement)**
12.  **substring(int beginIndex)  和 substring(int beginIndex, int endIndex)**
13.  **常量池**
14.  **intern()** 
15.  **String** **真的不可变吗**



### java.util：

#### Arrays

1. **asList**
2. **sort**
3. **binarySearch**
4. **copyOf**
5. **equals 和 deepEquals**
6. **fill**
7. **toString** **和** **deepToString**



#### ArrayList

1. **ArrayList** **定义**
2. **字段属性**
3. **构造函数**
4. **添加元素**
   - add()
   - offer
5. **删除元素**
   - remove(int index)
   - remove(E e)
6. **修改元素**
   - set(int index, E element) 
7. **查找元素**
   - get(int index)
8. **遍历集合**
   - **普通** **for** **循环遍历**
   - **迭代器** **iterator**
   - **迭代器的变种** **forEach** 即加强for循环
   - **迭代器** **ListIterator**
9. **SubList**
10. **size()**
11. **isEmpty()**
12. **trimToSize()**



#### LinkedList

1. **LinkedList** **定义**
2. **字段属性**
3. **构造函数**
4. **添加元素**
   - add()
   - offer
5. **删除元素**
   - remove(int index)
   - remove(E e)
6. **修改元素**
   - set(int index, E element) 
7. **查找元素**
   - get(int index)
8. **遍历集合**
   - **普通** **for** **循环**
   - **迭代器**
9. **迭代器和for循环效率差异**



#### HashMap

1. **哈希表**
2. **HashMap 定义**
3. **字段属性**
4. **构造函数**
5. **确定哈希桶数组索引位置**
6. **添加元素**
   - put(K key, V value) 
   - 
7. **扩容机制**
   - resize(int newCapacity)
8. **删除元素**
9. **查找元素**
10. **遍历元素**



#### HashSet

1. **HashSet** **定义**
2. **字段属性**
3. **构造函数**
4. **添加元素**
5. **删除元素**
6. **查找元素**
7. **遍历元素**



#### LinkedHashMap

1. **LinkedHashMap 定义**
2. **字段属性**
3. **构造函数**
4. **添加元素**
5. **删除元素**
6. **查找元素**
7. **遍历元素**
8. **迭代器**



#### LinkedHashSet

1. **LinkedHashSet 定义**
2. **构造函数**
3. **添加元素**
4. **删除元素**
5. **查找元素**
6. **遍历元素**