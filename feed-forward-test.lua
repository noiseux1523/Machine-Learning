require 'torch'
require 'nn'

---------------------
-- HYPERPARAMETERS --
---------------------

cmd = torch.CmdLine()
cmd:text()
cmd:text('Options for my NN')
cmd:option('-units', 71,'units in the hidden layer')
cmd:option('-learningRate', 0.1, 'learning rate')
-- etc...
cmd:text()
opt = cmd:parse(arg)

-----------
-- MODEL --
-----------

-- Feed-Forward Network
mlp = nn.Sequential()

-- Input Layer
inputSize = 71
hiddenLayer1Size = opt.units
hiddenLayer2Size = opt.units

-- Hidden Layer 1 -> Hidden Layer 2
mlp:add(nn.Linear(inputSize, hiddenLayer1Size))
mlp:add(nn.Tanh())
mlp:add(nn.Linear(hiddenLayer1Size, hiddenLayer2Size))
mlp:add(nn.Tanh())

-- Output Layer
nclasses = 2
mlp:add(nn.Linear(hiddenLayer2Size, nclasses))
mlp:add(nn.LogSoftMax())

print(mlp)

--------------
-- TRAINING --
--------------

-- Trainer
criterion = nn.ClassNLLCriterion() 
trainer = nn.StochasticGradient(mlp, criterion)
trainer.learningRate = opt.learningRate

----------
-- DATA --
----------

-- Function to split data by semicolon
function string:splitAtSemiColon()
  local sep, values = ";", {}
  local pattern = string.format("([^%s]+)", sep)
  self:gsub(pattern, function(c) values[#values+1] = c end)
  return values
end

-- Function to load the data
function loadData(dataFile)
  local dataset = {}
  local i = 1
  -- Save the input values and the target class for each line
  for line in io.lines(dataFile) do
    local values = line:splitAtSemiColon()
    local y = torch.Tensor(1)
    y[1] = values[#values] -- the target class is the last number in the line
    values[#values] = nil
    local x = torch.Tensor(values) -- the input data is all the other numbers
    dataset[i] = {x, y}
    i = i + 1
  end
  -- Return the size of the dataset (required for trainer)
  function dataset:size() return (i - 1) end -- the requirement mentioned
  return dataset
end

-- Load the data and train the model
dataset = loadData("train-set-delta.csv")
trainer:train(dataset)

-------------------------------
-- PREDICTION AND EVALUATION --
-------------------------------

-- Testing
x = torch.randn(71)
y = mlp:forward(x)
print(y) -- returns the log probability of each class

-- Function to get maximum value
function argmax(v)
  local maxvalue = torch.max(v)
  for i=1,v:size(1) do
    if v[i] == maxvalue then
      return i
    end
  end
end

-- Accuracy Computation
tot = 0
pos = 0
for line in io.lines("train-set-delta.csv") do
  -- Get the values from test file
  values = line:splitAtSemiColon()
  local y = torch.Tensor(1)
  y[1] = values[#values]
  values[#values] = nil
  local x = torch.Tensor(values)
  -- Get the most accurate (with argmax) prediction from the input values of test file
  local prediction = argmax(mlp:forward(x))
  -- Evaluate if prediction and real value is the same
  if math.floor(prediction) == math.floor(y[1]) then
    pos = pos + 1
  end
  tot = tot + 1
end
print("Accuracy(%) is " .. pos/tot*100)

-- Save Model
print("Weights of saved model: ")
-- print(mlp:get(1).weight) 
-- mlp:get(1) is the first module of mlp, i.e. nn.Linear(10 -> 10)
-- mlp:get(1).weight is the weight matrix of that layer
torch.save('file.th', mlp)

-- Load Model
mlp2 = torch.load('file.th')
print("Weights of saved model:")
-- print(mlp2:get(1).weight) -- this will print the exact same matrix













