"""
扫描 settings.json 里的版本列表，输出 GitHub Actions 的 matrix include 结构。
用法：python .github/workflows/scripts/matrix.py（需要设置 GITHUB_OUTPUT）
"""

import json
import os
import sys


def main():
	target_env = os.environ.get('TARGET_SUBPROJECT', '')
	targets = list(filter(None, target_env.split(',') if target_env else []))
	print('target_subprojects: {}'.format(targets))

	with open('settings.json') as f:
		settings = json.load(f)

	if not targets:
		subprojects = settings['versions']
	else:
		subprojects = []
		for subproject in settings['versions']:
			if subproject in targets:
				subprojects.append(subproject)
				targets.remove(subproject)
		if targets:
			print('Unexpected subprojects: {}'.format(targets), file=sys.stderr)
			sys.exit(1)

	matrix = {'include': [{'subproject': s} for s in subprojects]}
	output = os.environ.get('GITHUB_OUTPUT')
	if output:
		with open(output, 'a') as f:
			f.write('matrix={}\n'.format(json.dumps(matrix)))

	print('matrix:')
	print(json.dumps(matrix, indent=2))


if __name__ == '__main__':
	main()
