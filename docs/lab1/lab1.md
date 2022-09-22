# 编译原理实验

> HITSZ 2022 秋

## 实验一：词法分析

本实验目的为从 `input_code.txt` 输入代码，从 `coding_map.csv` 读取编码表，然后进行词法分析。

### 文法定义

在本次实验中定义一个 C 语言子集“TXT”语言的部分文法如下：

$G=(V,T,P,S)$，$V=\{S,A,B,C,digit,no\_0\_digit,char\}$，$T=\{任意符号\}$

定义 $P$ 如下：

$letter:=$ "a" | "A" | "b" | "B" | "c" | "C" | "d" | "D" | "e" | "E" | "f" | "F" | "g" | "G" | "h" | "H" | "i" | "I" | "j" | "J" | "k" | "K" | "l" | "L" | "m" | "M" | "n" | "N" | "o" | "O" | "p" | "P" | "q" | "Q" | "r" | "R" | "s" | "S" | "t" | "T" | "u" | "U" | "v" | "V" | "w" | "W" | "x" | "X" | "y" | "Y" | "z" | "Z" | "_"

$no\_0\_digit:=$ "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"

$digit:=$ "0" | $no\_0\_digit$

标识符：$S \rightarrow letter\ A$，$A\rightarrow letter\ A | digit \ A | \varepsilon$

整常数：$S->no\_0\_digit\ B$，$B\rightarrow digit\ B | \varepsilon$

运算符：$S\rightarrow C$，$C\rightarrow =|*|+|-|/$

### 自动机设计

根据上文文法，可以得到这些正则表达式：

1. `id->letter(letter|digit)*`
2. `intConst->no_0_digit(digit)*`
3. `multi->"*"`
4. `assign->"*" "*"`
5. `parentheBegin->"("`
6. `bracketEnd->")"`
7. `semicolon->";"`
8. `add->"+"`
9. `minus->"-"`
10. `div->"/"`
11. `comma->"."`

将得到的正则表达式合并为状态转化图：

![image-20220922162332175](lab1.assets/image-20220922162332175.png)

