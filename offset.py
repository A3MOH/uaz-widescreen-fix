#calculate offset for UI/HUD element. x is desired element position in 16:9 space. The result is the offset to apply to the element in .scr file 
#example: python offset.py --x 1536.0 gives 1.06666667 - this value should be placed in the .scr file to LogicPos(x,y) x value to the correct position in 16:9 space. For example, if you want to place an element at 1536.0 in 16:9 space, you would use LogicPos(1.06666667,y) in the .scr file.
import argparse

parser = argparse.ArgumentParser(description="")
parser.add_argument('--x', type=float, required=True, help="base value to calculate offset")

args = parser.parse_args()

result = float (args.x/1440.0)
print(f'{result:.8f}')